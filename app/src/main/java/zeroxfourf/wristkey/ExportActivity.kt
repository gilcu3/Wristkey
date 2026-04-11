package zeroxfourf.wristkey
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import wristkey.R
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportActivity : AppCompatActivity() {

    lateinit var mfaCodesTimer: Timer

    lateinit var utilities: Utilities

    private lateinit var clock: TextView

    private lateinit var qrExportButton: CardView
    private lateinit var fileExportButton: CardView

    private lateinit var backButton: CardView

    private var logins: List<Utilities.MfaCode> = emptyList()
    var loginNumber = 0

    private val qrExportLauncher: androidx.activity.result.ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (logins.size > 2 && loginNumber < logins.size) {
            val intent = Intent(applicationContext, QRCodeActivity::class.java)
            intent.putExtra(utilities.INTENT_QR_DATA, utilities.encodeOtpAuthURL(logins[loginNumber]))
            loginNumber += 1
            qrExportLauncher.launch(intent)
        } else {
            Toast.makeText(applicationContext, "Done!", Toast.LENGTH_SHORT).show()
            qrExportButton.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        utilities = Utilities (applicationContext)
        mfaCodesTimer = Timer()

        val data = utilities.getData()
        logins = data.otpauth.mapNotNull { utilities.decodeOtpAuthURL(it) }

        initializeUI()
        startClock()

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



    private fun initializeUI () {

        clock = findViewById(R.id.clock)

        qrExportButton = findViewById (R.id.qrExportButton)
        fileExportButton = findViewById (R.id.fileExportButton)

        backButton = findViewById (R.id.backButton)

        //logins = utilities.getLogins()

        qrExportButton.setOnClickListener {
            exportViaQrCodes()
        }

        fileExportButton.setOnClickListener {
            exportViaFile()
        }

        backButton.setOnClickListener {
            finish()
        }
    }


    private fun exportViaFile () {

        if (logins.isEmpty()) {
            Toast.makeText(this, "Your vault is empty!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val directory = File (applicationContext.filesDir.toString())

        val rfc3339Timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        val filename = directory.absolutePath + '/' + rfc3339Timestamp + ".wfs"

        Log.d ("Wristkey", "Writing export file to: " + applicationContext.filesDir.toString())

        val writer = FileWriter(filename)
        val vaultJson = utilities.objectMapper.writeValueAsString(utilities.getData())
        writer.write(vaultJson)
        writer.flush()
        writer.close()

        Toast.makeText(this, "Exported Wristkey vault to ${directory.absolutePath}", Toast.LENGTH_LONG).show()
        finish()

    }


    private fun exportViaQrCodes() {

        if (logins.isEmpty()) {
            Toast.makeText(this, "Your vault is empty!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val intent = Intent (applicationContext, QRCodeActivity::class.java)
        intent.putExtra (utilities.INTENT_QR_DATA, utilities.encodeOtpAuthURL(logins[loginNumber]))
        loginNumber += 1
        qrExportLauncher.launch(intent)

    }


    private fun startClock () {
        if (!utilities.db.getBoolean(utilities.SETTINGS_CLOCK_ENABLED, true)) clock.visibility = View.GONE

        try {
            mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { clock.text = utilities.getTime() }
                }
            }, 0, 1000)
        } catch (_: IllegalStateException) { }
    }

}