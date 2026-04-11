package zeroxfourf.wristkey

import android.content.Intent
import android.media.audiofx.HapticGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import wristkey.R
import java.io.File
import java.util.*

class FileImportActivity : AppCompatActivity() {

    lateinit var mfaCodesTimer: Timer
    lateinit var utilities: Utilities

    private lateinit var clock: TextView

    var isRound: Boolean = false

    lateinit var backButton: Button
    lateinit var pickFileButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_import)

        utilities = Utilities (applicationContext)
        mfaCodesTimer = Timer()

        initializeUI()

    }

    private fun initializeUI () {
        setContentView(R.layout.activity_file_import)

        clock = findViewById(R.id.clock)
        startClock()

        pickFileButton = findViewById (R.id.filePickerButton)
        backButton = findViewById (R.id.backButton)

        isRound = utilities.db.getBoolean (utilities.CONFIG_SCREEN_ROUND, resources.configuration.isScreenRound)

        backButton.setOnClickListener {
            backButton.performHapticFeedback(HapticGenerator.SUCCESS)
            finish()
        }

        pickFileButton.setOnClickListener {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please grant file access permission", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                showFilePicker()
            }
        }

    }

    private fun showFilePicker() {
        setContentView(R.layout.import_loading_screen)

        clock = findViewById(R.id.clock)
        startClock()

        val title = findViewById<TextView>(R.id.title)
        val description = findViewById<TextView>(R.id.description)
        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val progressRound = findViewById<CircularProgressIndicator>(R.id.progressRound)
        val doneButton: Button = findViewById(R.id.doneButton)
        val container = findViewById<LinearLayout>(R.id.buttonContainer)

        progress.visibility = View.GONE
        progressRound.visibility = View.GONE
        title.text = "Select a file"

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val jsonFiles = downloadDir.listFiles { file ->
            file.isFile && (file.extension.equals("json", ignoreCase = true) ||
                    file.extension.equals("txt", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (jsonFiles.isEmpty()) {
            description.text = "No JSON files found in Download folder.\n\nPush a file via ADB:\nadb push export.json /sdcard/Download/"
            doneButton.visibility = View.VISIBLE
            doneButton.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_prev)!!, null, null, null)
            doneButton.text = "Back"
            doneButton.setOnClickListener { finish() }
            return
        }

        description.text = "Found ${jsonFiles.size} file(s):"
        doneButton.visibility = View.GONE

        val spacing = (5 * resources.displayMetrics.density).toInt()
        val hPad = (16 * resources.displayMetrics.density).toInt()
        val vPad = (10 * resources.displayMetrics.density).toInt()

        for (file in jsonFiles) {
            val fileIcon = getDrawable(R.drawable.ic_outline_insert_drive_file_24)!!.mutate()
            fileIcon.setTint(android.graphics.Color.WHITE)
            val button = Button(this, null, 0, R.style.Wristkey_Button).apply {
                text = file.name
                setPadding(hPad, vPad, hPad, vPad)
                setCompoundDrawablesWithIntrinsicBounds(fileIcon, null, null, null)
                compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
                setOnClickListener { readData(file) }
            }
            container.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, spacing, 0, spacing)
            })
        }

        val backBtn = Button(this, null, 0, R.style.Wristkey_Button).apply {
            text = "Back"
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundResource(R.drawable.pill_shape_simple)
            setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_prev), null, null, null)
            setOnClickListener { finish() }
        }
        container.addView(backBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setMargins(0, spacing, 0, spacing)
        })
    }

    private fun startClock () {
        if (!utilities.db.getBoolean(utilities.SETTINGS_CLOCK_ENABLED, true)) clock.visibility = View.GONE

        try {
            mfaCodesTimer.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread { clock.text = utilities.getTime() }
                }
            }, 0, 1000)
        } catch (_: IllegalStateException) { }
    }

    override fun onStop() {
        super.onStop()
        mfaCodesTimer.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        mfaCodesTimer.cancel()
        finish()
    }

    override fun onStart() {
        super.onStart()
        mfaCodesTimer = Timer()
    }

    private fun readData(file: File) {
        setContentView(R.layout.import_loading_screen)

        clock = findViewById(R.id.clock)
        startClock()

        var logins: MutableList<Utilities.MfaCode> = mutableListOf()

        val title = findViewById<TextView>(R.id.title)
        val description = findViewById<TextView>(R.id.description)

        val progress = findViewById<LinearProgressIndicator>(R.id.progress)
        val progressRound = findViewById<CircularProgressIndicator>(R.id.progressRound)

        if (isRound) {
            progress.visibility = View.GONE
            progressRound.visibility = View.VISIBLE
        } else {
            progress.visibility = View.VISIBLE
            progressRound.visibility = View.GONE
        }

        val doneButton: Button = findViewById(R.id.doneButton)

        description.text = "Reading data"
        doneButton.visibility = View.GONE

        fun setNegative(message: String) {
            title.text = "Error"
            description.text = message

            progress.visibility = View.GONE
            progressRound.visibility = View.GONE
            doneButton.visibility = View.VISIBLE

            doneButton.setCompoundDrawablesWithIntrinsicBounds (getDrawable(R.drawable.ic_prev)!!, null, null, null)
            doneButton.text = "Go back"
            doneButton.setOnClickListener { finish() }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileData = file.readText()

                // Check if this is an encrypted Aegis vault
                try {
                    val json = JSONObject(fileData)
                    if (utilities.isEncryptedAegisVault(json)) {
                        withContext(Dispatchers.Main) { promptForPassword(file, title, description, progress, progressRound, doneButton) }
                        return@launch
                    }
                } catch (_: JSONException) { }

                try { logins.addAll(utilities.bitwardenToWristkey(JSONObject(fileData))) } catch (_: Exception) { }
                try { logins.addAll(utilities.aegisToWristkey(JSONObject(fileData))) } catch (_: Exception) { }
                try { logins.addAll(utilities.andOtpToWristkey(JSONArray(fileData))) } catch (_: Exception) { }
                if (logins.isEmpty()) throw NoSuchFieldException()
                withContext(Dispatchers.Main) { showImportResult(logins, title, description, progress, progressRound, doneButton) }
            } catch (noDirectory: NullPointerException) {
                withContext(Dispatchers.Main) { setNegative("Couldn't access file.") }
            } catch (invalidFile: JSONException) {
                withContext(Dispatchers.Main) { setNegative("Invalid file. Please follow the instructions on the previous screen.") }
            } catch (noData: NoSuchFieldException) {
                withContext(Dispatchers.Main) { setNegative("No data found in file. It may be corrupt or may have no 2FA secrets in it.") }
            }
        }

    }

    private fun showImportResult(
        logins: List<Utilities.MfaCode>,
        title: TextView, description: TextView,
        progress: LinearProgressIndicator, progressRound: CircularProgressIndicator,
        doneButton: Button
    ) {
        title.text = "Import from file"
        description.text = "Imported ${logins.size} account(s)!"
        description.append("\n\n")
        for ((index, login) in logins.withIndex()) description.append("${if (index != 0) " ⋅ " else ""}${login.issuer}")
        progress.visibility = View.GONE
        progressRound.visibility = View.GONE
        doneButton.visibility = View.VISIBLE
        doneButton.text = "Save"
        doneButton.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.outline_save_24)!!, null, null, null)
        doneButton.setOnClickListener {
            logins.forEach { utilities.overwriteLogin(utilities.encodeOtpAuthURL(it)) }
            finishAffinity()
            startActivity(Intent(applicationContext, MainActivity::class.java))
        }
    }

    private fun promptForPassword(
        file: File,
        title: TextView, description: TextView,
        progress: LinearProgressIndicator, progressRound: CircularProgressIndicator,
        doneButton: Button
    ) {
        progress.visibility = View.GONE
        progressRound.visibility = View.GONE
        title.text = "Encrypted vault"
        description.text = "Enter your Aegis password:"

        val container = findViewById<LinearLayout>(R.id.buttonContainer)
        val passwordInput = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
        }
        container.addView(passwordInput)

        val spacing = (5 * resources.displayMetrics.density).toInt()
        val hPad = (16 * resources.displayMetrics.density).toInt()
        val vPad = (10 * resources.displayMetrics.density).toInt()

        doneButton.visibility = View.VISIBLE
        doneButton.text = "Decrypt"
        doneButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        doneButton.setOnClickListener {
            val password = passwordInput.text.toString()
            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            doneButton.visibility = View.GONE
            passwordInput.visibility = View.GONE
            description.text = "Decrypting..."
            progress.visibility = if (isRound) View.GONE else View.VISIBLE
            progressRound.visibility = if (isRound) View.VISIBLE else View.GONE

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fileData = file.readText()
                    val decryptedDb = utilities.decryptAegisVault(fileData, password)
                    val wrapped = JSONObject().put("db", decryptedDb)
                    val logins = utilities.aegisToWristkey(wrapped)
                    if (logins.isEmpty()) throw NoSuchFieldException()
                    withContext(Dispatchers.Main) { showImportResult(logins, title, description, progress, progressRound, doneButton) }
                } catch (e: IllegalArgumentException) {
                    withContext(Dispatchers.Main) {
                        description.text = "Wrong password. Try again:"
                        progress.visibility = View.GONE
                        progressRound.visibility = View.GONE
                        passwordInput.visibility = View.VISIBLE
                        passwordInput.text.clear()
                        doneButton.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FileImport", "Decrypt failed", e)
                    withContext(Dispatchers.Main) {
                        title.text = "Error"
                        description.text = "Failed to decrypt vault: ${e.javaClass.simpleName}: ${e.message}"
                        progress.visibility = View.GONE
                        progressRound.visibility = View.GONE
                        doneButton.visibility = View.VISIBLE
                        doneButton.text = "Go back"
                        doneButton.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_prev)!!, null, null, null)
                        doneButton.setOnClickListener { finish() }
                    }
                }
            }
        }
    }

}
