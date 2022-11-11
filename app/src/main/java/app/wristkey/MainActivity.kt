package app.wristkey

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.turingcomplete.kotlinonetimepassword.*
import wristkey.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.properties.Delegates

public const val CODE_AUTHENTICATION_VERIFICATION = 241

class MainActivity : AppCompatActivity() {

    lateinit var mfaCodesTimer: Timer
    lateinit var utilities: Utilities

    private lateinit var clock: TextView
    private lateinit var roundTimeLeft: ProgressBar
    private lateinit var loginsRecycler: RecyclerView
    private lateinit var squareTimeLeft: ProgressBar
    private lateinit var addAccountButton: CardView
    private lateinit var settingsButton: CardView
    private lateinit var aboutButton: CardView

    private lateinit var vault: kotlin.collections.List<Utilities.MfaCode>
    private lateinit var keys: kotlin.collections.List<String>

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        utilities = Utilities (applicationContext)
        mfaCodesTimer = Timer()

        vault = utilities.getVault()
        keys = utilities.vault.all.keys.toList() as List<String>

        initializeUI()

        startClock()

        start2faTimer()

        addAccountButton.setOnClickListener {
            startActivity(Intent(applicationContext, AddActivity::class.java))
            aboutButton.performHapticFeedback(HapticGenerator.SUCCESS)
        }

        aboutButton.setOnClickListener {
            startActivity(Intent(applicationContext, AboutActivity::class.java))
            aboutButton.performHapticFeedback(HapticGenerator.SUCCESS)
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
            aboutButton.performHapticFeedback(HapticGenerator.SUCCESS)
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!(resultCode == RESULT_OK && requestCode == CODE_AUTHENTICATION_VERIFICATION)) {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setShape () {
        if (
            utilities.vault.getBoolean (
                utilities.CONFIG_SCREEN_ROUND,
                resources.configuration.isScreenRound
            )
        ) {
            roundTimeLeft.visibility = View.VISIBLE
            squareTimeLeft.visibility = View.GONE
        } else {
            roundTimeLeft.visibility = View.GONE
            squareTimeLeft.visibility = View.VISIBLE
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeUI () {
        clock = findViewById(R.id.clock)

        loginsRecycler = findViewById(R.id.loginsRecycler)

        roundTimeLeft = findViewById(R.id.RoundTimeLeft)
        squareTimeLeft = findViewById(R.id.SquareTimeLeftTop)

        addAccountButton = findViewById(R.id.AddAccountButton)
        settingsButton = findViewById(R.id.SettingsButton)
        aboutButton = findViewById(R.id.AboutButton)

        setShape()

        val adapter = LoginsAdapter(utilities.getLogins().toMutableList())
        loginsRecycler.layoutManager = LinearLayoutManager(this@MainActivity)
        loginsRecycler.adapter = adapter
        loginsRecycler.invalidate()
        loginsRecycler.refreshDrawableState()
        loginsRecycler.scheduleLayoutAnimation()
        loginsRecycler.setItemViewCacheSize(vault.size)

        addAccountButton.animation = AnimationUtils.loadAnimation(applicationContext, R.anim.slide_right)
        settingsButton.animation = AnimationUtils.loadAnimation(applicationContext, R.anim.slide_right)
        aboutButton.animation = AnimationUtils.loadAnimation(applicationContext, R.anim.slide_right)

    }

    private fun start2faTimer () {
        try {
            thread {

                // round timer
                mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        val currentSecond = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                        var halfMinuteElapsed = abs((60-currentSecond))
                        if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                        try {
                            roundTimeLeft.progress = halfMinuteElapsed
                        } catch (_: Exception) {  }
                    }
                }, 0, 1000) // 1000 milliseconds = 1 second

                // square timer
                mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        val currentSecond = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                        var halfMinuteElapsed = abs((60-currentSecond))
                        if (halfMinuteElapsed >= 30) halfMinuteElapsed -= 30
                        try {
                            squareTimeLeft.progress = halfMinuteElapsed
                        } catch (_: Exception) {  }
                    }
                }, 0, 1000) // 1000 milliseconds = 1 second

            }
        } catch (_: IllegalStateException) {}
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun startClock () {

        if (!utilities.vault.getBoolean(utilities.SETTINGS_CLOCK_ENABLED, true)) {
            findViewById<CardView>(R.id.clockBackground).visibility = View.GONE
        }

        try {
            mfaCodesTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val currentHour24 = SimpleDateFormat("HH", Locale.getDefault()).format(Date())
                    val currentHour = SimpleDateFormat("hh", Locale.getDefault()).format(Date())
                    val currentMinute = SimpleDateFormat("mm", Locale.getDefault()).format(Date())
                    val currentSecond = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()
                    val currentAmPm = SimpleDateFormat("a", Locale.getDefault()).format(Date())
                    runOnUiThread {
                        try {

                            if (utilities.vault.getBoolean(utilities.SETTINGS_24H_CLOCK_ENABLED, false)) {
                                clock.text = "$currentHour24:$currentMinute"
                                if ((currentSecond % 2) == 0) clock.text = "$currentHour24 $currentMinute"
                            } else {
                                clock.text = "$currentHour:$currentMinute"
                                if ((currentSecond % 2) == 0) clock.text = "$currentHour $currentMinute"
                            }

                        } catch (_: Exception) { }
                    }
                }
            }, 0, 1000) // 1000 milliseconds = 1 second
        } catch (_: IllegalStateException) { }
    }

    inner class LoginsAdapter (private val logins: MutableList<Utilities.MfaCode>) : RecyclerView.Adapter<LoginsAdapter.ViewHolder>() {

        lateinit var blinkAnimation: AlphaAnimation
        lateinit var singleBlinkAnimation: AlphaAnimation
        var beepEnabled by Delegates.notNull<Boolean>()
        var hapticsEnabled by Delegates.notNull<Boolean>()

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : ViewHolder {  // create new views
            val loginCard: View = LayoutInflater.from(parent.context).inflate(R.layout.login_card, parent, false)
            loginCard.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ViewHolder(loginCard)
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onBindViewHolder(loginCard: ViewHolder, position: Int) {  // binds the list items to a view

            loginCard.setIsRecyclable(false)

            val login = logins[loginCard.adapterPosition]

            lateinit var algorithm: HmacAlgorithm
            if (login.algorithm!!.contains(utilities.ALGO_SHA1)) algorithm = HmacAlgorithm.SHA1
            else if (login.algorithm.contains(utilities.ALGO_SHA256)) algorithm = HmacAlgorithm.SHA256
            else if (login.algorithm.contains(utilities.ALGO_SHA512)) algorithm = HmacAlgorithm.SHA512

            loginCard.name.text = login.issuer

            if (!login.account.isNullOrBlank()) loginCard.label.text = login.account
            else if (!login.label.isNullOrBlank()) loginCard.label.text = login.label
            else loginCard.label.visibility = View.GONE

            var otp: String?

            when (login.mode) {
                utilities.MFA_TIME_MODE -> {

                    when (login.period) {
                        15, 60 -> {
                            loginCard.counterControls.visibility = View.VISIBLE
                            loginCard.incrementCounter.visibility = View.GONE
                            loginCard.decrementCounter.visibility = View.GONE
                            loginCard.counter.visibility = View.VISIBLE
                            loginCard.counter.text = "${login.period}s"
                        }

                        30 -> {
                            loginCard.counterControls.visibility = View.GONE
                        }

                    }

                    val config = TimeBasedOneTimePasswordConfig (
                        timeStep = login.period!!.toLong(),
                        timeStepUnit = TimeUnit.SECONDS,
                        codeDigits = login.digits!!,
                        hmacAlgorithm = algorithm
                    )

                    otp = TimeBasedOneTimePasswordGenerator(login.secret!!.toByteArray(), config)
                        .generate()

                    loginCard.code.text =
                        if (otp.length == 6) otp.replace("...".toRegex(), "$0 ") else otp.replace("....".toRegex(), "$0 ")

                    var timerElapsed = 0
                    try {
                        mfaCodesTimer.scheduleAtFixedRate (object : TimerTask() {
                            override fun run() {

                                try {
                                    val currentSecond = SimpleDateFormat("s", Locale.getDefault()).format(Date()).toInt()

                                    Log.d("asdasdas", timerElapsed.toString())

                                    if (timerElapsed == 1) {
                                        try {
                                            loginCard.code.startAnimation(blinkAnimation)
                                        } catch (_: Exception) { }

                                        if (beepEnabled) utilities.beep()
                                        if (hapticsEnabled)
                                            loginCard.incrementCounter.performHapticFeedback(HapticGenerator.ERROR)

                                    }

                                    when (login.period) {

                                        15 -> {
                                            if (currentSecond % 15 == 0) timerElapsed = 0
                                            else timerElapsed += 1
                                        }

                                        30 -> {
                                            if (currentSecond % 30 == 0) timerElapsed = 0
                                            else timerElapsed += 1
                                        }

                                        60 -> {
                                            timerElapsed = abs((60-currentSecond))
                                        }

                                    }

                                    loginCard.code.text =
                                        if (otp!!.length == 6) otp!!.replace("...".toRegex(), "$0 ") else otp!!.replace("....".toRegex(), "$0 ")

                                    otp = TimeBasedOneTimePasswordGenerator(login.secret.toByteArray(), config)
                                        .generate()

                                } catch (_: Exception) { }
                            }
                        }, 0, 1000) // 1000 milliseconds = 1 second
                    } catch (_: IllegalStateException) { }
                }

                utilities.MFA_COUNTER_MODE -> {

                    loginCard.counterControls.visibility = View.VISIBLE

                    val config = HmacOneTimePasswordConfig (
                        codeDigits = login.digits!!,
                        hmacAlgorithm = algorithm
                    )

                    var currentCount: Long

                    currentCount = login.counter!!
                    loginCard.counter.text = currentCount.toString()

                    try {

                        loginCard.incrementCounter.setOnClickListener {
                            currentCount = loginCard.counter.text.toString().toLong()
                            loginCard.counter.text = (currentCount + 1).toString()

                            otp = HmacOneTimePasswordGenerator(login.secret!!.toByteArray(), config)
                                .generate(loginCard.counter.text.toString().toLong())

                            loginCard.code.text =
                                if (otp!!.length == 6) otp!!.replace("...".toRegex(), "$0 ") else otp!!.replace("....".toRegex(), "$0 ")

                            try {
                                loginCard.code.startAnimation(singleBlinkAnimation)
                                loginCard.incrementCounter.startAnimation(singleBlinkAnimation)
                            } catch (_: Exception) { }

                            loginCard.incrementCounter.performHapticFeedback(HapticGenerator.ALREADY_EXISTS)
                            if (beepEnabled) utilities.beep()
                            if (hapticsEnabled) loginCard.incrementCounter.performHapticFeedback(HapticGenerator.ERROR)

                            if (currentCount+1 == 69L) Toast.makeText(applicationContext, "Nice ;)", Toast.LENGTH_SHORT).show()

                            val loginData = Utilities.MfaCode (
                                type = utilities.DEFAULT_TYPE,
                                mode = login.mode,
                                issuer = login.issuer,
                                account = login.account,
                                secret = login.secret,
                                algorithm = login.algorithm,
                                digits = login.digits,
                                period = login.period,
                                lock = false,
                                counter = loginCard.counter.text.toString().toLong(),
                                label = login.label
                            )

                            utilities.overwriteLogin(oldLogin = login, newLogin = loginData)

                        }

                        loginCard.decrementCounter.setOnClickListener {
                            currentCount = loginCard.counter.text.toString().toLong()
                            if (currentCount != 0L) {
                                loginCard.counter.text = (currentCount - 1).toString()
                            }

                            otp = HmacOneTimePasswordGenerator(login.secret!!.toByteArray(), config)
                                .generate(loginCard.counter.text.toString().toLong())

                            loginCard.code.text =
                                if (otp!!.length == 6) otp!!.replace("...".toRegex(), "$0 ") else otp!!.replace("....".toRegex(), "$0 ")

                            try {
                                loginCard.code.startAnimation(singleBlinkAnimation)
                                loginCard.incrementCounter.startAnimation(singleBlinkAnimation)
                            } catch (_: Exception) { }

                            loginCard.decrementCounter.performHapticFeedback(HapticGenerator.ALREADY_EXISTS)
                            if (beepEnabled) utilities.beep()
                            if (hapticsEnabled) loginCard.incrementCounter.performHapticFeedback(HapticGenerator.ERROR)

                            if (currentCount-1 == 69L) Toast.makeText(applicationContext, "Nice ;)", Toast.LENGTH_SHORT).show()

                            val loginData = Utilities.MfaCode (
                                type = utilities.DEFAULT_TYPE,
                                mode = login.mode,
                                issuer = login.issuer,
                                account = login.account,
                                secret = login.secret,
                                algorithm = login.algorithm,
                                digits = login.digits,
                                period = login.period,
                                lock = false,
                                counter = loginCard.counter.text.toString().toLong(),
                                label = login.label
                            )

                            utilities.overwriteLogin(oldLogin = login, newLogin = loginData)

                        }

                    } catch (_: IllegalStateException) { }

                    otp = HmacOneTimePasswordGenerator(login.secret!!.toByteArray(), config)
                        .generate(loginCard.counter.text.toString().toLong())

                    loginCard.code.text =
                        if (otp!!.length == 6) otp!!.replace("...".toRegex(), "$0 ") else otp!!.replace("....".toRegex(), "$0 ")

                }
            }

            // tap on totp / mfa / 2fa
            loginCard.code.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Wristkey", loginCard.code.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(applicationContext, "Code copied!", Toast.LENGTH_LONG).show()
            }

            loginCard.loginCard.setOnTouchListener(object : OnSwipeTouchListener(this@MainActivity) {
                override fun onClick() {
                    super.onClick()
                }

                override fun onSwipeRight() {
                    super.onSwipeRight()
                }

                override fun onLongClick() {
                    val intent = Intent(applicationContext, ManualEntryActivity::class.java)
                    intent.putExtra(utilities.INTENT_UUID, utilities.getUuid(login))
                    startActivity(intent)
                    loginCard.loginCard.performHapticFeedback(HapticGenerator.SUCCESS)
                    super.onSwipeRight()
                }

            })

        }

        override fun getItemCount(): Int {  // return the number of the items in the list
            return logins.size
        }

        @RequiresApi(Build.VERSION_CODES.M)
        inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {  // Holds the views for adding it to image and text
            val loginCard: CardView = itemView.findViewById(R.id.loginCard)

            val name: TextView = itemView.findViewById(R.id.name)
            val label: TextView = itemView.findViewById(R.id.label)
            val code: TextView = itemView.findViewById(R.id.code)

            val counterControls: LinearLayout = itemView.findViewById(R.id.counterControls)
            val incrementCounter: ImageView = itemView.findViewById(R.id.increment_counter)
            val counter: TextView = itemView.findViewById(R.id.counter)
            val decrementCounter: ImageView = itemView.findViewById(R.id.decrement_counter)

            init {
                name.isSelected = true
                label.isSelected = true

                blinkAnimation = AlphaAnimation (0.25f, 1f)
                blinkAnimation.duration = 500
                blinkAnimation.startOffset = 20
                blinkAnimation.repeatCount = 2

                singleBlinkAnimation = AlphaAnimation (0.25f, 1f)
                singleBlinkAnimation.duration = 500
                singleBlinkAnimation.startOffset = 20
                singleBlinkAnimation.repeatCount = 0

                beepEnabled = utilities.vault.getBoolean(utilities.SETTINGS_BEEP_ENABLED, true)
                hapticsEnabled = utilities.vault.getBoolean(utilities.SETTINGS_HAPTICS_ENABLED, true)

            }

        }
    }


}