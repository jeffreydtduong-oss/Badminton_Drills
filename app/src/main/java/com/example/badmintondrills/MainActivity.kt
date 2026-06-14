package com.example.badmintondrills

import android.os.*
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import java.util.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup

data class TrainingSettings(
    var currentDrill: String = "drill1",

    // Drill 1 specific settings
    var drill1TimeToShuttleMin: Float = 1.0f,
    var drill1TimeToShuttleMax: Float = 3.0f,
    var drill1TimeToCenterMin: Float = 1.0f,
    var drill1TimeToCenterMax: Float = 2.0f,
    var minShuttleNumber: Int = 1,
    var maxShuttleNumber: Int = 4,

    // Drill 2 specific settings
    var drill2TimeToShuttleMin: Float = 1.0f,
    var drill2TimeToShuttleMax: Float = 3.0f,

    // NEW: Separate time ranges for front and back court
    var drill2FrontCourtTimeToShuttleMin: Float = 1.0f,
    var drill2FrontCourtTimeToShuttleMax: Float = 2.0f,
    var drill2BackCourtTimeToShuttleMin: Float = 2.0f,
    var drill2BackCourtTimeToShuttleMax: Float = 3.0f,

    var numberDisplayTime: Float = 0.5f,
    var repMode: String = "infinite",
    var targetReps: Int = 10,
    var soundEnabled: Boolean = true,
    var speechRate: Float = 1.0f,  // 1.0 is normal, >1.0 is faster
    var speedMultiplier: Float = 1.0f,


    // Drill 2 shot probabilities
    var shotProbabilities: Map<String, Int> = mapOf(
        "net" to 50,
        "lift" to 50,
        "drop" to 40,
        "clear" to 40,
        "smash" to 20
    ),

    // Drill 2 shot-specific time back to center
    var shotTimeToCenter: Map<String, ShotTimeRange> = mapOf(
        "net" to ShotTimeRange(min = 1.0f, max = 1.5f),
        "lift" to ShotTimeRange(min = 1.5f, max = 2.5f),
        "drop" to ShotTimeRange(min = 1.5f, max = 2.0f),
        "clear" to ShotTimeRange(min = 2.0f, max = 3.0f),
        "smash" to ShotTimeRange(min = 2.0f, max = 2.5f)
    )
)

// New data class for time ranges
data class ShotTimeRange(
    var min: Float = 1.0f,
    var max: Float = 2.0f
)

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var countdownText: TextView
    private lateinit var countdownStatus: TextView
    private lateinit var shuttleNumberText: TextView
    private lateinit var timerBar: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var timerContainer: LinearLayout
    private lateinit var repCounterText: TextView
    private lateinit var startStopButton: Button
    private lateinit var settingsButton: Button
    private lateinit var soundToggle: SwitchCompat
    private lateinit var speedMultiplierButton: Button
    private lateinit var dynamicContentContainer: LinearLayout

    // State Variables
    private var isRunning = false
    private var currentPhase = Phase.IDLE
    private var currentRep = 0
    private var shuttleNumber = 1
    private var timeToShuttleDuration = 0f
    private var timeToCenterDuration = 0f
    private var countdownSeconds = 5
    private var countdownIndex = 0
    private val countdownWords = listOf("Ready", "Set", "Go")

    // Timers
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null
    private var shuttleTimerHandler: Handler? = null
    private var shuttleTimerRunnable: Runnable? = null
    private var centerTimerHandler: Handler? = null
    private var centerTimerRunnable: Runnable? = null

    // Settings
    private lateinit var settings: TrainingSettings

    // TTS
    private lateinit var textToSpeech: TextToSpeech
    private var ttsInitialized = false

    // SharedPreferences keys
    private val prefsName = "BadmintonTrainingPrefs"
    private val keySettings = "training_settings"

    private enum class Phase {
        IDLE, COUNTDOWN, SHUTTLE_NUMBER, TIME_TO_SHUTTLE, TIME_TO_CENTER
    }

    private lateinit var drillSelectButton: Button
    private lateinit var drillNameText: TextView
    private lateinit var shotChoiceText: TextView

    // For Drill 2
    private var shotChoice = ""

    // UI Elements - Add direction arrow text view
    private lateinit var directionArrowText: TextView

    // Add these constants for directions
    companion object {
        private val FRONT_COURT_SHOTS = listOf("net", "lift")
        private val REAR_COURT_SHOTS = listOf("drop", "clear", "smash")
        private val NET_DIRECTIONS = listOf("left", "middle", "right")
        private val LIFT_DIRECTIONS = listOf("left", "right")
        private val DROP_DIRECTIONS = listOf("left", "middle", "right")
        private val CLEAR_DIRECTIONS = listOf("left", "right")
        private val SMASH_DIRECTIONS = listOf("left", "middle", "right")
        private val ARROW_SYMBOLS = mapOf(
            "left" to "←",
            "middle" to "↓",  // or "•" or "●"
            "right" to "→"
        )
    }

    // Add direction variable
    private var shotDirection = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()
        initializeViews()
        loadSettings() // This initializes the 'settings' variable
        updateDrillName() // Add this here - AFTER loadSettings()
        initializeTextToSpeech()
        setupButtonListeners()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    private fun initializeViews() {
        countdownText = findViewById(R.id.countdownText)
        countdownStatus = findViewById(R.id.countdownStatus)
        shuttleNumberText = findViewById(R.id.shuttleNumberText)
        shotChoiceText = findViewById(R.id.shotChoiceText)
        directionArrowText = findViewById(R.id.directionArrowText)
        dynamicContentContainer = findViewById(R.id.dynamicContentContainer)

        timerBar = findViewById(R.id.timerBar)
        timerText = findViewById(R.id.timerText)
        timerContainer = findViewById(R.id.timerContainer)

        repCounterText = findViewById(R.id.repCounterText)
        startStopButton = findViewById(R.id.startStopButton)
        settingsButton = findViewById(R.id.settingsButton)
        soundToggle = findViewById(R.id.soundToggle)
        drillSelectButton = findViewById(R.id.drillSelectButton)
        drillNameText = findViewById(R.id.drillNameText)
        speedMultiplierButton = findViewById(R.id.speedMultiplierButton)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = prefs.getString(keySettings, null)
        settings = if (!json.isNullOrEmpty()) {
            try {
                Gson().fromJson(json, TrainingSettings::class.java)
            } catch (e: Exception) {
                TrainingSettings()
            }
        } else {
            TrainingSettings()
        }

        // Remove the old migration code that's causing the error
        // Just use the settings as is

        soundToggle.isChecked = settings.soundEnabled
        repCounterText.text = "Reps: 0"
        updateSpeedMultiplierButton()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = Gson().toJson(settings)
        prefs.edit().putString(keySettings, json).apply()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInitialized = true
                textToSpeech.language = Locale.getDefault()
                textToSpeech.setSpeechRate(settings.speechRate)  // Add this line
            }
        }
    }

    private fun setupButtonListeners() {
        startStopButton.setOnClickListener {
            if (isRunning) {
                stopTraining()
            } else {
                startTraining()
            }
        }

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        soundToggle.setOnCheckedChangeListener { _, isChecked ->
            settings.soundEnabled = isChecked
            saveSettings()
            if (isChecked) {
                speak("Sound enabled")
            }
        }

        drillSelectButton.setOnClickListener {
            showDrillSelectionDialog()
        }

        speedMultiplierButton.setOnClickListener {
            showSpeedMultiplierDialog()
        }
    }

    private fun startTraining() {
        isRunning = true
        currentPhase = Phase.COUNTDOWN
        currentRep = 0
        startStopButton.text = "STOP"
        repCounterText.text = "Reps: 0"

        updateDisplay()
        startCountdown()
    }

    private fun stopTraining() {
        isRunning = false
        currentPhase = Phase.IDLE
        startStopButton.text = "START"

        // Clear all handlers
        countdownHandler?.removeCallbacksAndMessages(null)
        shuttleTimerHandler?.removeCallbacksAndMessages(null)
        centerTimerHandler?.removeCallbacksAndMessages(null)

        updateDisplay()
        speak("Training stopped")
    }

    private fun startCountdown() {
        countdownIndex = 0
        countdownSeconds = 5

        // Make sure countdown text is visible and centered
        countdownText.visibility = View.VISIBLE
        dynamicContentContainer.visibility = View.GONE
        timerBar.visibility = View.INVISIBLE
        timerText.visibility = View.INVISIBLE

        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                when (countdownIndex) {
                    0 -> {
                        countdownText.text = countdownWords[0]
                        if (settings.soundEnabled) speak(countdownWords[0])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 1500)
                    }
                    1 -> {
                        countdownText.text = countdownWords[1]
                        if (settings.soundEnabled) speak(countdownWords[1])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 1500)
                    }
                    2 -> {
                        countdownText.text = countdownWords[2]
                        if (settings.soundEnabled) speak(countdownWords[2])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 2000)
                    }
                    3 -> {
                        // Countdown complete
                        countdownText.visibility = View.GONE
                        currentPhase = Phase.SHUTTLE_NUMBER
                        startShuttleNumberPhase()
                    }
                }
            }
        }

        countdownHandler?.post(countdownRunnable!!)
    }

    private fun startShuttleNumberPhase() {
        // Randomly select shuttle number
        shuttleNumber = if (settings.currentDrill == "drill2") {
            (1..4).random()
        } else {
            (settings.minShuttleNumber..settings.maxShuttleNumber).random()
        }

        // Make sure dynamic content container is visible
        dynamicContentContainer.visibility = View.VISIBLE

        shuttleNumberText.text = shuttleNumber.toString()
        shuttleNumberText.visibility = View.VISIBLE

        // HIDE the status text
        countdownStatus.visibility = View.GONE

        // For Drill 2, also show shot choice
        if (settings.currentDrill == "drill2") {
            val (shotType, direction) = getRandomShotAndDirection(shuttleNumber)
            shotChoice = shotType
            shotDirection = direction

            // Update UI with larger text
            shotChoiceText.text = when (shotType) {
                "net" -> "NET SHOT"
                "lift" -> "LIFT"
                "drop" -> "DROP"
                "clear" -> "CLEAR"
                "smash" -> "SMASH"
                else -> shotType.uppercase()
            }

            // Show direction arrow
            directionArrowText.text = ARROW_SYMBOLS[direction] ?: ""
            directionArrowText.visibility = View.VISIBLE
            shotChoiceText.visibility = View.VISIBLE

            if (settings.soundEnabled) {
                val spokenText = when (shotType) {
                    "net" -> "$shuttleNumber, net shot $direction"
                    "lift" -> "$shuttleNumber, lift $direction"
                    "drop" -> "$shuttleNumber, drop shot $direction"
                    "clear" -> "$shuttleNumber, clear $direction"
                    "smash" -> "$shuttleNumber, smash $direction"
                    else -> "$shuttleNumber, $shotType"
                }
                speak(spokenText)
            }
        } else {
            shotChoiceText.visibility = View.GONE
            directionArrowText.visibility = View.GONE
            if (settings.soundEnabled) {
                speak(shuttleNumber.toString())
            }
        }

        // Show shuttle number for configured time
        shuttleTimerHandler = Handler(Looper.getMainLooper())
        shuttleTimerRunnable = Runnable {
            startTimeToShuttlePhase()
        }

        val displayTimeMs = (settings.numberDisplayTime * 1000).toLong()
        shuttleTimerHandler?.postDelayed(shuttleTimerRunnable!!, displayTimeMs)
    }

    private fun startTimeToShuttlePhase() {
        currentPhase = Phase.TIME_TO_SHUTTLE

        // Get the appropriate time range based on current drill and shuttle number
        val (minTime, maxTime) = if (settings.currentDrill == "drill1") {
            Pair(settings.drill1TimeToShuttleMin, settings.drill1TimeToShuttleMax)
        } else {
            // For Drill 2, use different ranges based on shuttle number
            when (shuttleNumber) {
                1, 2 -> Pair(settings.drill2FrontCourtTimeToShuttleMin, settings.drill2FrontCourtTimeToShuttleMax)
                3, 4 -> Pair(settings.drill2BackCourtTimeToShuttleMin, settings.drill2BackCourtTimeToShuttleMax)
                else -> Pair(settings.drill2TimeToShuttleMin, settings.drill2TimeToShuttleMax) // fallback
            }
        }

        // APPLY SPEED MULTIPLIER HERE
        val adjustedMin = minTime / settings.speedMultiplier  // Divide because higher speed = less time
        val adjustedMax = maxTime / settings.speedMultiplier

        // Randomly select time between adjusted min and max
        val random = Random()
        timeToShuttleDuration = adjustedMin + random.nextFloat() * (adjustedMax - adjustedMin)

        val totalMilliseconds = (timeToShuttleDuration * 1000).toLong()
        var elapsedMilliseconds = 0L

        // Show timer bar
        timerBar.max = 1000
        timerBar.progress = 1000
        timerBar.progressDrawable = getDrawable(R.drawable.custom_progress_green)
        timerText.text = String.format("%.1f", timeToShuttleDuration)
        timerBar.visibility = View.VISIBLE
        timerText.visibility = View.VISIBLE

        // Show the status text with court position info for Drill 2
        countdownStatus.visibility = View.VISIBLE
        countdownStatus.text = if (settings.currentDrill == "drill2") {
            val courtPosition = if (shuttleNumber in 1..2) "FRONT COURT" else "BACK COURT"
            "Time to shuttle ($courtPosition):"
        } else {
            "Time to shuttle:"
        }

        // Keep dynamic content visible for drill 2
        if (settings.currentDrill == "drill2") {
            dynamicContentContainer.visibility = View.VISIBLE
            shuttleNumberText.visibility = View.VISIBLE
            shotChoiceText.visibility = View.VISIBLE
            directionArrowText.visibility = View.VISIBLE
        } else {
            dynamicContentContainer.visibility = View.GONE
        }

        shuttleTimerHandler = Handler(Looper.getMainLooper())
        shuttleTimerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                elapsedMilliseconds += 50

                val progress = (elapsedMilliseconds.toFloat() / totalMilliseconds.toFloat() * 1000).toInt()
                val remainingTime = timeToShuttleDuration - (elapsedMilliseconds / 1000f)

                timerBar.progress = 1000 - progress
                timerText.text = String.format("%.1f", remainingTime)

                if (elapsedMilliseconds >= totalMilliseconds) {
                    // Time to shuttle complete
                    timerBar.visibility = View.INVISIBLE
                    timerText.visibility = View.INVISIBLE

                    // Hide shot and direction after "Hit" command
                    if (settings.currentDrill == "drill2") {
                        dynamicContentContainer.visibility = View.GONE
                    }

                    if (settings.soundEnabled) speak("Hit")
                    startTimeToCenterPhase()
                } else {
                    shuttleTimerHandler?.postDelayed(this, 50)
                }
            }
        }

        shuttleTimerHandler?.post(shuttleTimerRunnable!!)
    }

    private fun startTimeToCenterPhase() {
        currentPhase = Phase.TIME_TO_CENTER

        // Get appropriate time range based on current drill
        val (minTime, maxTime) = if (settings.currentDrill == "drill1") {
            // For Drill 1, use the basic time to center
            Pair(settings.drill1TimeToCenterMin, settings.drill1TimeToCenterMax)
        } else {
            // For Drill 2, use shot-specific time ranges
            val timeRange = settings.shotTimeToCenter[shotChoice]
            if (timeRange != null) {
                Pair(timeRange.min, timeRange.max)
            } else {
                // Fallback if shot not found
                Pair(1.0f, 2.0f)
            }
        }

        // APPLY SPEED MULTIPLIER HERE
        val adjustedMin = minTime / settings.speedMultiplier  // Divide because higher speed = less time
        val adjustedMax = maxTime / settings.speedMultiplier

        // Randomly select time between adjusted min and max
        val random = Random()
        timeToCenterDuration = adjustedMin + random.nextFloat() * (adjustedMax - adjustedMin)

        val totalMilliseconds = (timeToCenterDuration * 1000).toLong()
        var elapsedMilliseconds = 0L

        // Show timer bar
        timerBar.max = 1000
        timerBar.progress = 1000
        timerBar.progressDrawable = getDrawable(R.drawable.custom_progress_orange)
        timerText.text = String.format("%.1f", timeToCenterDuration)
        timerBar.visibility = View.VISIBLE
        timerText.visibility = View.VISIBLE

        // Show the status text with shot-specific info for Drill 2
        countdownStatus.visibility = View.VISIBLE
        if (settings.currentDrill == "drill2" && shotChoice.isNotEmpty()) {
            countdownStatus.text = "Time back to center after ${shotChoice.uppercase()}:"
        } else {
            countdownStatus.text = "Time back to center:"
        }

        // Hide dynamic content container
        dynamicContentContainer.visibility = View.GONE

        centerTimerHandler = Handler(Looper.getMainLooper())
        centerTimerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                elapsedMilliseconds += 50

                val progress = (elapsedMilliseconds.toFloat() / totalMilliseconds.toFloat() * 1000).toInt()
                val remainingTime = timeToCenterDuration - (elapsedMilliseconds / 1000f)

                timerBar.progress = 1000 - progress
                timerText.text = String.format("%.1f", remainingTime)

                if (elapsedMilliseconds >= totalMilliseconds) {
                    // Time to center complete
                    timerBar.visibility = View.INVISIBLE
                    timerText.visibility = View.INVISIBLE

                    // Increment rep counter
                    currentRep++
                    repCounterText.text = "Reps: $currentRep"

                    // Check if we should stop
                    if (settings.repMode == "fixed" && currentRep >= settings.targetReps) {
                        stopTraining()
                        Toast.makeText(this@MainActivity,
                            "Training complete! Completed $currentRep reps.",
                            Toast.LENGTH_LONG).show()
                    } else {
                        // Start next rep
                        countdownStatus.visibility = View.GONE
                        startShuttleNumberPhase()
                    }
                } else {
                    centerTimerHandler?.postDelayed(this, 50)
                }
            }
        }

        centerTimerHandler?.post(centerTimerRunnable!!)
    }

    private fun updateDisplay() {
        when (currentPhase) {
            Phase.IDLE -> {
                countdownText.visibility = View.GONE
                dynamicContentContainer.visibility = View.GONE
                timerBar.visibility = View.INVISIBLE
                timerText.visibility = View.INVISIBLE
                countdownStatus.text = "Press START to begin"
            }
            Phase.COUNTDOWN -> {
                dynamicContentContainer.visibility = View.GONE
                timerBar.visibility = View.INVISIBLE
                timerText.visibility = View.INVISIBLE
                countdownText.visibility = View.VISIBLE
            }
            Phase.SHUTTLE_NUMBER -> {
                countdownText.visibility = View.GONE
                timerBar.visibility = View.INVISIBLE
                timerText.visibility = View.INVISIBLE
                dynamicContentContainer.visibility = View.VISIBLE
            }
            Phase.TIME_TO_SHUTTLE -> {
                countdownText.visibility = View.GONE
                // Timer and content visibility handled in startTimeToShuttlePhase()
            }
            Phase.TIME_TO_CENTER -> {
                countdownText.visibility = View.GONE
                dynamicContentContainer.visibility = View.GONE
                // Timer visibility handled in startTimeToCenterPhase()
            }
        }
    }

    private fun speak(text: String) {
        if (ttsInitialized && settings.soundEnabled) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.settings_dialog, null)

        // Common views
        val numberDisplayTimeEdit = dialogView.findViewById<EditText>(R.id.numberDisplayTimeEdit)
        val repModeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.repModeRadioGroup)
        val targetRepsEdit = dialogView.findViewById<EditText>(R.id.targetRepsEdit)
        val infiniteRepsRadio = dialogView.findViewById<RadioButton>(R.id.infiniteRepsRadio)
        val fixedRepsRadio = dialogView.findViewById<RadioButton>(R.id.fixedRepsRadio)
        val speechRateSeekBar = dialogView.findViewById<SeekBar>(R.id.speechRateSeekBar)
        val speechRateValue = dialogView.findViewById<TextView>(R.id.speechRateValue)

        // Drill-specific settings containers
        val drill1Settings = dialogView.findViewById<LinearLayout>(R.id.drill1Settings)
        val drill2Settings = dialogView.findViewById<LinearLayout>(R.id.drill2Settings)
        val currentDrillText = dialogView.findViewById<TextView>(R.id.currentDrillText)

        // Drill 1 specific fields
        val minShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.minShuttleNumberEdit)
        val maxShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.maxShuttleNumberEdit)
        val timeToShuttleMinEdit = dialogView.findViewById<EditText>(R.id.timeToShuttleMinEdit)
        val timeToShuttleMaxEdit = dialogView.findViewById<EditText>(R.id.timeToShuttleMaxEdit)
        val timeToCenterMinEdit = dialogView.findViewById<EditText>(R.id.timeToCenterMinEdit)
        val timeToCenterMaxEdit = dialogView.findViewById<EditText>(R.id.timeToCenterMaxEdit)

        // Drill 2 specific fields
        val frontCourtTimeMinEdit = dialogView.findViewById<EditText>(R.id.frontCourtTimeMinEdit)
        val frontCourtTimeMaxEdit = dialogView.findViewById<EditText>(R.id.frontCourtTimeMaxEdit)
        val backCourtTimeMinEdit = dialogView.findViewById<EditText>(R.id.backCourtTimeMinEdit)
        val backCourtTimeMaxEdit = dialogView.findViewById<EditText>(R.id.backCourtTimeMaxEdit)

        // Drill 2 shot probabilities
        val netProbEdit = dialogView.findViewById<EditText>(R.id.netProbEdit)
        val liftProbEdit = dialogView.findViewById<EditText>(R.id.liftProbEdit)
        val dropProbEdit = dialogView.findViewById<EditText>(R.id.dropProbEdit)
        val clearProbEdit = dialogView.findViewById<EditText>(R.id.clearProbEdit)
        val smashProbEdit = dialogView.findViewById<EditText>(R.id.smashProbEdit)

        // Drill 2 shot-specific time back to center fields
        val netTimeMinEdit = dialogView.findViewById<EditText>(R.id.netTimeMinEdit)
        val netTimeMaxEdit = dialogView.findViewById<EditText>(R.id.netTimeMaxEdit)
        val liftTimeMinEdit = dialogView.findViewById<EditText>(R.id.liftTimeMinEdit)
        val liftTimeMaxEdit = dialogView.findViewById<EditText>(R.id.liftTimeMaxEdit)
        val dropTimeMinEdit = dialogView.findViewById<EditText>(R.id.dropTimeMinEdit)
        val dropTimeMaxEdit = dialogView.findViewById<EditText>(R.id.dropTimeMaxEdit)
        val clearTimeMinEdit = dialogView.findViewById<EditText>(R.id.clearTimeMinEdit)
        val clearTimeMaxEdit = dialogView.findViewById<EditText>(R.id.clearTimeMaxEdit)
        val smashTimeMinEdit = dialogView.findViewById<EditText>(R.id.smashTimeMinEdit)
        val smashTimeMaxEdit = dialogView.findViewById<EditText>(R.id.smashTimeMaxEdit)

        // Set current drill display
        val drillName = when (settings.currentDrill) {
            "drill1" -> "Drill #1 - Random Footwork"
            "drill2" -> "Drill #2 - Random Footwork with Shots"
            else -> "Drill #1 - Random Footwork"
        }
        currentDrillText.text = drillName

        // Show/hide drill-specific settings
        when (settings.currentDrill) {
            "drill1" -> {
                drill1Settings.visibility = View.VISIBLE
                drill2Settings.visibility = View.GONE
            }
            "drill2" -> {
                drill1Settings.visibility = View.GONE
                drill2Settings.visibility = View.VISIBLE
            }
        }

        // Load Drill 1 specific values
        minShuttleNumberEdit.setText(settings.minShuttleNumber.toString())
        maxShuttleNumberEdit.setText(settings.maxShuttleNumber.toString())
        timeToShuttleMinEdit.setText(settings.drill1TimeToShuttleMin.toString())
        timeToShuttleMaxEdit.setText(settings.drill1TimeToShuttleMax.toString())
        timeToCenterMinEdit.setText(settings.drill1TimeToCenterMin.toString())
        timeToCenterMaxEdit.setText(settings.drill1TimeToCenterMax.toString())

        // Load Drill 2 specific values
        frontCourtTimeMinEdit.setText(settings.drill2FrontCourtTimeToShuttleMin.toString())
        frontCourtTimeMaxEdit.setText(settings.drill2FrontCourtTimeToShuttleMax.toString())
        backCourtTimeMinEdit.setText(settings.drill2BackCourtTimeToShuttleMin.toString())
        backCourtTimeMaxEdit.setText(settings.drill2BackCourtTimeToShuttleMax.toString())

        // Load common values
        numberDisplayTimeEdit.setText(settings.numberDisplayTime.toString())
        targetRepsEdit.setText(settings.targetReps.toString())
        val currentRate = settings.speechRate
        speechRateSeekBar.progress = (currentRate * 10).toInt() - 5  // Convert 0.5-2.0 to 0-15 range
        speechRateValue.text = String.format("%.1fx", currentRate)

        // Load Drill 2 shot probabilities
        netProbEdit.setText(settings.shotProbabilities["net"].toString())
        liftProbEdit.setText(settings.shotProbabilities["lift"].toString())
        dropProbEdit.setText(settings.shotProbabilities["drop"].toString())
        clearProbEdit.setText(settings.shotProbabilities["clear"].toString())
        smashProbEdit.setText(settings.shotProbabilities["smash"].toString())

        // Load Drill 2 shot-specific time ranges
        settings.shotTimeToCenter["net"]?.let {
            netTimeMinEdit.setText(it.min.toString())
            netTimeMaxEdit.setText(it.max.toString())
        }
        settings.shotTimeToCenter["lift"]?.let {
            liftTimeMinEdit.setText(it.min.toString())
            liftTimeMaxEdit.setText(it.max.toString())
        }
        settings.shotTimeToCenter["drop"]?.let {
            dropTimeMinEdit.setText(it.min.toString())
            dropTimeMaxEdit.setText(it.max.toString())
        }
        settings.shotTimeToCenter["clear"]?.let {
            clearTimeMinEdit.setText(it.min.toString())
            clearTimeMaxEdit.setText(it.max.toString())
        }
        settings.shotTimeToCenter["smash"]?.let {
            smashTimeMinEdit.setText(it.min.toString())
            smashTimeMaxEdit.setText(it.max.toString())
        }

        // Update display when slider changes
        speechRateSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = (progress + 5) / 10f  // Convert 0-15 to 0.5-2.0
                speechRateValue.text = String.format("%.1fx", rate)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set rep mode
        if (settings.repMode == "infinite") {
            infiniteRepsRadio.isChecked = true
            targetRepsEdit.isEnabled = false
        } else {
            fixedRepsRadio.isChecked = true
            targetRepsEdit.isEnabled = true
        }

        repModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            targetRepsEdit.isEnabled = checkedId == R.id.fixedRepsRadio
        }

        // Create the dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Training Settings")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .create()

        // Set custom positive button listener
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                try {
                    var validationFailed = false
                    var errorMessage = ""

                    // Save common settings
                    settings.numberDisplayTime = numberDisplayTimeEdit.text.toString().toFloat()
                    settings.repMode = if (infiniteRepsRadio.isChecked) "infinite" else "fixed"
                    if (settings.repMode == "fixed") {
                        settings.targetReps = targetRepsEdit.text.toString().toInt()
                    }
                    settings.speechRate = (speechRateSeekBar.progress + 5) / 10f
                    textToSpeech.setSpeechRate(settings.speechRate)  // Apply immediately

                    // Save drill-specific settings based on current drill
                    when (settings.currentDrill) {
                        "drill1" -> {
                            settings.minShuttleNumber = minShuttleNumberEdit.text.toString().toInt()
                            settings.maxShuttleNumber = maxShuttleNumberEdit.text.toString().toInt()
                            settings.drill1TimeToShuttleMin = timeToShuttleMinEdit.text.toString().toFloat()
                            settings.drill1TimeToShuttleMax = timeToShuttleMaxEdit.text.toString().toFloat()
                            settings.drill1TimeToCenterMin = timeToCenterMinEdit.text.toString().toFloat()
                            settings.drill1TimeToCenterMax = timeToCenterMaxEdit.text.toString().toFloat()

                            if (settings.minShuttleNumber >= settings.maxShuttleNumber) {
                                validationFailed = true
                                errorMessage = "Min number must be less than max number"
                            }
                            if (settings.drill1TimeToShuttleMin > settings.drill1TimeToShuttleMax) {
                                validationFailed = true
                                errorMessage = "Time to shuttle min must be ≤ max"
                            }
                            if (settings.drill1TimeToCenterMin > settings.drill1TimeToCenterMax) {
                                validationFailed = true
                                errorMessage = "Time to center min must be ≤ max"
                            }
                        }
                        "drill2" -> {
                            // Save Drill 2 time to shuttle
                            val frontMin = frontCourtTimeMinEdit.text.toString().toFloat()
                            val frontMax = frontCourtTimeMaxEdit.text.toString().toFloat()
                            val backMin = backCourtTimeMinEdit.text.toString().toFloat()
                            val backMax = backCourtTimeMaxEdit.text.toString().toFloat()

                            if (frontMin > frontMax) {
                                validationFailed = true
                                errorMessage = "Front court time to shuttle min must be ≤ max"
                            } else if (backMin > backMax) {
                                validationFailed = true
                                errorMessage = "Back court time to shuttle min must be ≤ max"
                            } else if (frontMin < 0 || frontMax < 0 || backMin < 0 || backMax < 0) {
                                validationFailed = true
                                errorMessage = "Times must be positive"
                            } else {
                                settings.drill2FrontCourtTimeToShuttleMin = frontMin
                                settings.drill2FrontCourtTimeToShuttleMax = frontMax
                                settings.drill2BackCourtTimeToShuttleMin = backMin
                                settings.drill2BackCourtTimeToShuttleMax = backMax
                            }

                            // Save shot probabilities
                            if (!validationFailed) {
                                val netProb = netProbEdit.text.toString().toInt()
                                val liftProb = liftProbEdit.text.toString().toInt()
                                val dropProb = dropProbEdit.text.toString().toInt()
                                val clearProb = clearProbEdit.text.toString().toInt()
                                val smashProb = smashProbEdit.text.toString().toInt()

                                if (netProb < 0 || liftProb < 0 || dropProb < 0 || clearProb < 0 || smashProb < 0) {
                                    validationFailed = true
                                    errorMessage = "Probabilities must be non-negative (0-100)"
                                } else if (netProb > 100 || liftProb > 100 || dropProb > 100 || clearProb > 100 || smashProb > 100) {
                                    validationFailed = true
                                    errorMessage = "Probabilities must be ≤ 100%"
                                } else {
                                    val frontTotal = netProb + liftProb
                                    if (frontTotal != 100) {
                                        validationFailed = true
                                        errorMessage = "Sum of Front court shots: $frontTotal% (must be 100%)"
                                    } else {
                                        val rearTotal = dropProb + clearProb + smashProb
                                        if (rearTotal != 100) {
                                            validationFailed = true
                                            errorMessage = "Sum of Rear court shots: $rearTotal% (must be 100%)"
                                        } else {
                                            settings.shotProbabilities = mapOf(
                                                "net" to netProb,
                                                "lift" to liftProb,
                                                "drop" to dropProb,
                                                "clear" to clearProb,
                                                "smash" to smashProb
                                            )
                                        }
                                    }
                                }
                            }

                            // Save shot-specific time ranges
                            if (!validationFailed) {
                                val netMin = netTimeMinEdit.text.toString().toFloat()
                                val netMax = netTimeMaxEdit.text.toString().toFloat()
                                val liftMin = liftTimeMinEdit.text.toString().toFloat()
                                val liftMax = liftTimeMaxEdit.text.toString().toFloat()
                                val dropMin = dropTimeMinEdit.text.toString().toFloat()
                                val dropMax = dropTimeMaxEdit.text.toString().toFloat()
                                val clearMin = clearTimeMinEdit.text.toString().toFloat()
                                val clearMax = clearTimeMaxEdit.text.toString().toFloat()
                                val smashMin = smashTimeMinEdit.text.toString().toFloat()
                                val smashMax = smashTimeMaxEdit.text.toString().toFloat()

                                if (netMin > netMax || netMin < 0 || netMax < 0) {
                                    validationFailed = true
                                    errorMessage = "Invalid Net shot time range"
                                } else if (liftMin > liftMax || liftMin < 0 || liftMax < 0) {
                                    validationFailed = true
                                    errorMessage = "Invalid Lift time range"
                                } else if (dropMin > dropMax || dropMin < 0 || dropMax < 0) {
                                    validationFailed = true
                                    errorMessage = "Invalid Drop shot time range"
                                } else if (clearMin > clearMax || clearMin < 0 || clearMax < 0) {
                                    validationFailed = true
                                    errorMessage = "Invalid Clear time range"
                                } else if (smashMin > smashMax || smashMin < 0 || smashMax < 0) {
                                    validationFailed = true
                                    errorMessage = "Invalid Smash time range"
                                } else {
                                    settings.shotTimeToCenter = mapOf(
                                        "net" to ShotTimeRange(min = netMin, max = netMax),
                                        "lift" to ShotTimeRange(min = liftMin, max = liftMax),
                                        "drop" to ShotTimeRange(min = dropMin, max = dropMax),
                                        "clear" to ShotTimeRange(min = clearMin, max = clearMax),
                                        "smash" to ShotTimeRange(min = smashMin, max = smashMax)
                                    )
                                }
                            }
                        }
                    }

                    // Common validation
                    if (!validationFailed && settings.numberDisplayTime <= 0) {
                        validationFailed = true
                        errorMessage = "Display time must be positive"
                    }

                    if (!validationFailed && settings.repMode == "fixed" && settings.targetReps <= 0) {
                        validationFailed = true
                        errorMessage = "Target reps must be positive"
                    }

                    if (validationFailed) {
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        saveSettings()
                        Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }

                } catch (e: NumberFormatException) {
                    Toast.makeText(this@MainActivity, "Please enter valid numbers in all fields", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Invalid input values: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ttsInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        // Clean up handlers
        countdownHandler?.removeCallbacksAndMessages(null)
        shuttleTimerHandler?.removeCallbacksAndMessages(null)
        centerTimerHandler?.removeCallbacksAndMessages(null)
    }

    private fun updateDrillName() {
        val drillName = when (settings.currentDrill) {
            "drill1" -> "Drill #1 - Random Footwork"
            "drill2" -> "Drill #2 - Random Footwork with Shots"
            else -> "Drill #1 - Random Footwork"
        }
        drillNameText.text = drillName
    }

    private fun showDrillSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.drill_select_dialog, null)

        val drillRadioGroup = dialogView.findViewById<RadioGroup>(R.id.drillRadioGroup)
        val drill1Radio = dialogView.findViewById<RadioButton>(R.id.drill1Radio)
        val drill2Radio = dialogView.findViewById<RadioButton>(R.id.drill2Radio)

        when (settings.currentDrill) {
            "drill1" -> drill1Radio.isChecked = true
            "drill2" -> drill2Radio.isChecked = true
        }

        AlertDialog.Builder(this)
            .setTitle("Select Drill")
            .setView(dialogView)
            .setPositiveButton("Select") { dialog, which ->
                val selectedDrill = when (drillRadioGroup.checkedRadioButtonId) {
                    R.id.drill1Radio -> "drill1"
                    R.id.drill2Radio -> "drill2"
                    else -> "drill1"
                }

                if (settings.currentDrill != selectedDrill) {
                    settings.currentDrill = selectedDrill
                    saveSettings()
                    updateDrillName()

                    // Reset any running training
                    if (isRunning) {
                        stopTraining()
                        Toast.makeText(this, "Drill changed. Please restart training.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getRandomShotAndDirection(shuttleNumber: Int): Pair<String, String> {
        return when (shuttleNumber) {
            1, 2 -> {
                // Front court shots (net or lift)
                val random = Random()
                val total = (settings.shotProbabilities["net"] ?: 50) + (settings.shotProbabilities["lift"] ?: 50)
                val randValue = random.nextInt(total)

                val shotType = if (randValue < (settings.shotProbabilities["net"] ?: 50)) {
                    "net"
                } else {
                    "lift"
                }

                val direction = when (shotType) {
                    "net" -> NET_DIRECTIONS.random()
                    "lift" -> LIFT_DIRECTIONS.random()
                    else -> "middle" // fallback
                }

                Pair(shotType, direction)
            }
            3, 4 -> {
                // Rear court shots (drop, clear, or smash)
                val random = Random()
                val dropProb = settings.shotProbabilities["drop"] ?: 40
                val clearProb = settings.shotProbabilities["clear"] ?: 40
                val smashProb = settings.shotProbabilities["smash"] ?: 20
                val total = dropProb + clearProb + smashProb
                val randValue = random.nextInt(total)

                val shotType = when {
                    randValue < dropProb -> "drop"
                    randValue < dropProb + clearProb -> "clear"
                    else -> "smash"
                }

                val direction = when (shotType) {
                    "drop" -> DROP_DIRECTIONS.random()
                    "clear" -> CLEAR_DIRECTIONS.random()
                    "smash" -> SMASH_DIRECTIONS.random()
                    else -> "middle" // fallback
                }

                Pair(shotType, direction)
            }
            else -> Pair("net", "middle") // fallback
        }
    }

    private fun showSpeedMultiplierDialog() {
        val dialogView = layoutInflater.inflate(R.layout.speed_multiplier_dialog, null)

        val seekBar = dialogView.findViewById<SeekBar>(R.id.speedMultiplierSeekBar)
        val valueText = dialogView.findViewById<TextView>(R.id.speedMultiplierValue)
        val minText = dialogView.findViewById<TextView>(R.id.speedMinText)
        val maxText = dialogView.findViewById<TextView>(R.id.speedMaxText)

        // Set range: 0.5x to 2.0x (or adjust as needed)
        val minMultiplier = 0.5f
        val maxMultiplier = 2.0f
        val currentMultiplier = settings.speedMultiplier

        // Convert multiplier to progress (0-100 range for easier calculation)
        val progress = ((currentMultiplier - minMultiplier) / (maxMultiplier - minMultiplier) * 100).toInt()
        seekBar.progress = progress
        valueText.text = String.format("%.1fx", currentMultiplier)
        minText.text = String.format("%.1fx", minMultiplier)
        maxText.text = String.format("%.1fx", maxMultiplier)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val multiplier = minMultiplier + (progress / 100f) * (maxMultiplier - minMultiplier)
                valueText.text = String.format("%.1fx", multiplier)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("Speed Multiplier")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val multiplier = minMultiplier + (seekBar.progress / 100f) * (maxMultiplier - minMultiplier)
                settings.speedMultiplier = multiplier
                saveSettings()
                updateSpeedMultiplierButton()
                Toast.makeText(this, "Speed set to ${String.format("%.1f", multiplier)}x", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSpeedMultiplierButton() {
        speedMultiplierButton.text = String.format("Speed: %.1fx", settings.speedMultiplier)
    }
}