package com.example.badmintondrills

import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.gson.Gson
import java.util.*

data class TrainingSettings(
    var currentDrill: String = "drill1", // "drill1" or "drill2"
    var timeToShuttleMin: Float = 1.0f,
    var timeToShuttleMax: Float = 3.0f,
    var timeToCenterMin: Float = 1.0f,
    var timeToCenterMax: Float = 2.0f,
    var repMode: String = "infinite", // "infinite" or "fixed"
    var targetReps: Int = 10,
    var soundEnabled: Boolean = true,

    // Drill 1 specific
    var minShuttleNumber: Int = 1,    // Minimum shuttle number (e.g., 1)
    var maxShuttleNumber: Int = 4,    // Maximum shuttle number (e.g., 6)
    var numberDisplayTime: Float = 0.5f,  // Time to display number in seconds

    // Drill 2 specific - shot probabilities (percentages)
    var shotProbabilities: Map<String, Int> = mapOf(
        "net" to 50,
        "lift" to 50,
        "drop" to 40,
        "clear" to 40,
        "smash" to 20
    )
)

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var countdownText: TextView
    private lateinit var countdownStatus: TextView
    private lateinit var shuttleNumberText: TextView
    private lateinit var timerBar1: ProgressBar
    private lateinit var timerBar2: ProgressBar
    private lateinit var timerText1: TextView
    private lateinit var timerText2: TextView
    private lateinit var repCounterText: TextView
    private lateinit var startStopButton: Button
    private lateinit var settingsButton: Button
    private lateinit var soundToggle: SwitchCompat

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
        timerBar1 = findViewById(R.id.timerBar1)
        timerBar2 = findViewById(R.id.timerBar2)
        timerText1 = findViewById(R.id.timerText1)
        timerText2 = findViewById(R.id.timerText2)
        repCounterText = findViewById(R.id.repCounterText)
        startStopButton = findViewById(R.id.startStopButton)
        settingsButton = findViewById(R.id.settingsButton)
        soundToggle = findViewById(R.id.soundToggle)
        drillSelectButton = findViewById(R.id.drillSelectButton)
        drillNameText = findViewById(R.id.drillNameText)
        directionArrowText = findViewById(R.id.directionArrowText)

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

        soundToggle.isChecked = settings.soundEnabled
        repCounterText.text = "Reps: 0"
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

        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                when (countdownIndex) {
                    0 -> {
                        countdownText.text = countdownWords[0]
                        countdownText.visibility = View.VISIBLE
                        if (settings.soundEnabled) speak(countdownWords[0])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 1500) // 1.5 seconds for "Ready"
                    }
                    1 -> {
                        countdownText.text = countdownWords[1]
                        if (settings.soundEnabled) speak(countdownWords[1])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 1500) // 1.5 seconds for "Set"
                    }
                    2 -> {
                        countdownText.text = countdownWords[2]
                        if (settings.soundEnabled) speak(countdownWords[2])
                        countdownIndex++
                        countdownHandler?.postDelayed(this, 2000) // 2 seconds for "Go"
                    }
                    3 -> {
                        // Countdown complete, start shuttle number phase
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
        // Randomly select shuttle number between min and max (inclusive)
        shuttleNumber = if (settings.currentDrill == "drill2") {
            // For drill 2, always 1-4
            (1..4).random()
        } else {
            // For drill 1, use configured range
            (settings.minShuttleNumber..settings.maxShuttleNumber).random()
        }

        shuttleNumberText.text = shuttleNumber.toString()
        directionArrowText.visibility = View.GONE // Hide arrow initially

        // HIDE the status text
        countdownStatus.visibility = View.GONE
        shuttleNumberText.visibility = View.VISIBLE

        // For Drill 2, also show shot choice
        if (settings.currentDrill == "drill2") {
            val (shotType, direction) = getRandomShotAndDirection(shuttleNumber)
            shotChoice = shotType
            shotDirection = direction

            // Update UI
            shotChoiceText.text = when (shotType) {
                "net" -> "Net shot"
                "lift" -> "Lift"
                "drop" -> "Drop shot"
                "clear" -> "Clear"
                "smash" -> "Smash"
                else -> shotType
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
            if (settings.soundEnabled) {
                speak(shuttleNumber.toString())
            }
        }

        // Show shuttle number (and shot choice) for configured time
        shuttleTimerHandler = Handler(Looper.getMainLooper())
        shuttleTimerRunnable = Runnable {
            shuttleNumberText.visibility = View.GONE
            shotChoiceText.visibility = View.GONE
            directionArrowText.visibility = View.GONE // Hide arrow
            startTimeToShuttlePhase()
        }

        // Use the configured display time (convert seconds to milliseconds)
        val displayTimeMs = (settings.numberDisplayTime * 1000).toLong()
        shuttleTimerHandler?.postDelayed(shuttleTimerRunnable!!, displayTimeMs)
    }

    private fun startTimeToShuttlePhase() {
        currentPhase = Phase.TIME_TO_SHUTTLE

        // Randomly select time between min and max
        val random = Random()
        timeToShuttleDuration = settings.timeToShuttleMin +
                random.nextFloat() * (settings.timeToShuttleMax - settings.timeToShuttleMin)

        val totalMilliseconds = (timeToShuttleDuration * 1000).toLong()
        var elapsedMilliseconds = 0L

        timerBar1.max = 1000
        timerBar1.progress = 1000
        timerText1.text = String.format("%.1f", timeToShuttleDuration)
        timerBar1.visibility = View.VISIBLE
        timerText1.visibility = View.VISIBLE

        // ADD THIS LINE - Show the status text
        countdownStatus.visibility = View.VISIBLE
        countdownStatus.text = "Time to shuttle:"

        shuttleTimerHandler = Handler(Looper.getMainLooper())
        shuttleTimerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                elapsedMilliseconds += 50 // Update every 50ms for smooth animation

                val progress = (elapsedMilliseconds.toFloat() / totalMilliseconds.toFloat() * 1000).toInt()
                val remainingTime = timeToShuttleDuration - (elapsedMilliseconds / 1000f)

                timerBar1.progress = 1000 - progress
                timerText1.text = String.format("%.1f", remainingTime)

                if (elapsedMilliseconds >= totalMilliseconds) {
                    // Time to shuttle complete
                    timerBar1.visibility = View.GONE
                    timerText1.visibility = View.GONE
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

        // Randomly select time between min and max
        val random = Random()
        timeToCenterDuration = settings.timeToCenterMin +
                random.nextFloat() * (settings.timeToCenterMax - settings.timeToCenterMin)

        val totalMilliseconds = (timeToCenterDuration * 1000).toLong()
        var elapsedMilliseconds = 0L

        timerBar2.max = 1000
        timerBar2.progress = 1000
        timerText2.text = String.format("%.1f", timeToCenterDuration)
        timerBar2.visibility = View.VISIBLE
        timerText2.visibility = View.VISIBLE

        // ADD THIS LINE - Show the status text
        countdownStatus.visibility = View.VISIBLE
        countdownStatus.text = "Time back to center:"

        centerTimerHandler = Handler(Looper.getMainLooper())
        centerTimerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                elapsedMilliseconds += 50 // Update every 50ms for smooth animation

                val progress = (elapsedMilliseconds.toFloat() / totalMilliseconds.toFloat() * 1000).toInt()
                val remainingTime = timeToCenterDuration - (elapsedMilliseconds / 1000f)

                timerBar2.progress = 1000 - progress
                timerText2.text = String.format("%.1f", remainingTime)

                if (elapsedMilliseconds >= totalMilliseconds) {
                    // Time to center complete
                    timerBar2.visibility = View.GONE
                    timerText2.visibility = View.GONE

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
                        // Start next rep - HIDE STATUS TEXT BEFORE SHOWING NUMBER
                        countdownStatus.visibility = View.GONE  // ← ADD THIS LINE
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
        // Hide shot choice and direction arrow text by default
        shotChoiceText.visibility = View.GONE
        directionArrowText.visibility = View.GONE

        when (currentPhase) {
            Phase.IDLE -> {
                countdownText.visibility = View.GONE
                shuttleNumberText.visibility = View.GONE
                timerBar1.visibility = View.GONE
                timerBar2.visibility = View.GONE
                timerText1.visibility = View.GONE
                timerText2.visibility = View.GONE
                countdownStatus.text = "Press START to begin"
            }
            Phase.COUNTDOWN -> {
                shuttleNumberText.visibility = View.GONE
                timerBar1.visibility = View.GONE
                timerBar2.visibility = View.GONE
                timerText1.visibility = View.GONE
                timerText2.visibility = View.GONE
            }
            Phase.SHUTTLE_NUMBER -> {
                countdownText.visibility = View.GONE
                timerBar1.visibility = View.GONE
                timerBar2.visibility = View.GONE
                timerText1.visibility = View.GONE
                timerText2.visibility = View.GONE
                // Note: shuttleNumberText, shotChoiceText, and directionArrowText visibility
                // are handled separately in startShuttleNumberPhase()
            }
            Phase.TIME_TO_SHUTTLE -> {
                countdownText.visibility = View.GONE
                shuttleNumberText.visibility = View.GONE
                shotChoiceText.visibility = View.GONE
                directionArrowText.visibility = View.GONE
                timerBar2.visibility = View.GONE
                timerText2.visibility = View.GONE
            }
            Phase.TIME_TO_CENTER -> {
                countdownText.visibility = View.GONE
                shuttleNumberText.visibility = View.GONE
                shotChoiceText.visibility = View.GONE
                directionArrowText.visibility = View.GONE
                timerBar1.visibility = View.GONE
                timerText1.visibility = View.GONE
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

        val timeToShuttleMinEdit = dialogView.findViewById<EditText>(R.id.timeToShuttleMinEdit)
        val timeToShuttleMaxEdit = dialogView.findViewById<EditText>(R.id.timeToShuttleMaxEdit)
        val timeToCenterMinEdit = dialogView.findViewById<EditText>(R.id.timeToCenterMinEdit)
        val timeToCenterMaxEdit = dialogView.findViewById<EditText>(R.id.timeToCenterMaxEdit)
        val repModeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.repModeRadioGroup)
        val targetRepsEdit = dialogView.findViewById<EditText>(R.id.targetRepsEdit)
        val infiniteRepsRadio = dialogView.findViewById<RadioButton>(R.id.infiniteRepsRadio)
        val fixedRepsRadio = dialogView.findViewById<RadioButton>(R.id.fixedRepsRadio)

        // Drill-specific settings containers
        val drill1Settings = dialogView.findViewById<LinearLayout>(R.id.drill1Settings)
        val drill2Settings = dialogView.findViewById<LinearLayout>(R.id.drill2Settings)
        val currentDrillText = dialogView.findViewById<TextView>(R.id.currentDrillText)

        // Drill 1 specific fields
        val minShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.minShuttleNumberEdit)
        val maxShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.maxShuttleNumberEdit)

        // COMMON field: Number display time (now common to both drills)
        val numberDisplayTimeEdit = dialogView.findViewById<EditText>(R.id.numberDisplayTimeEdit)

        // Drill 2 specific fields (shot probabilities)
        val netProbEdit = dialogView.findViewById<EditText>(R.id.netProbEdit)
        val liftProbEdit = dialogView.findViewById<EditText>(R.id.liftProbEdit)
        val dropProbEdit = dialogView.findViewById<EditText>(R.id.dropProbEdit)
        val clearProbEdit = dialogView.findViewById<EditText>(R.id.clearProbEdit)
        val smashProbEdit = dialogView.findViewById<EditText>(R.id.smashProbEdit)

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

        // Set common values (including number display time)
        timeToShuttleMinEdit.setText(settings.timeToShuttleMin.toString())
        timeToShuttleMaxEdit.setText(settings.timeToShuttleMax.toString())
        timeToCenterMinEdit.setText(settings.timeToCenterMin.toString())
        timeToCenterMaxEdit.setText(settings.timeToCenterMax.toString())
        targetRepsEdit.setText(settings.targetReps.toString())
        numberDisplayTimeEdit.setText(settings.numberDisplayTime.toString()) // COMMON

        // Set drill 1 specific values
        minShuttleNumberEdit.setText(settings.minShuttleNumber.toString())
        maxShuttleNumberEdit.setText(settings.maxShuttleNumber.toString())

        // Set drill 2 specific values (shot probabilities)
        netProbEdit.setText(settings.shotProbabilities["net"].toString())
        liftProbEdit.setText(settings.shotProbabilities["lift"].toString())
        dropProbEdit.setText(settings.shotProbabilities["drop"].toString())
        clearProbEdit.setText(settings.shotProbabilities["clear"].toString())
        smashProbEdit.setText(settings.shotProbabilities["smash"].toString())

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

        // Create the dialog first with null positive button listener
        val dialog = AlertDialog.Builder(this)
            .setTitle("Training Settings")
            .setView(dialogView)
            .setPositiveButton("Save", null) // Set to null initially
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .create()

        // Set custom positive button listener to prevent automatic dismissal
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                try {
                    // Save COMMON settings (for both drills)
                    settings.timeToShuttleMin = timeToShuttleMinEdit.text.toString().toFloat()
                    settings.timeToShuttleMax = timeToShuttleMaxEdit.text.toString().toFloat()
                    settings.timeToCenterMin = timeToCenterMinEdit.text.toString().toFloat()
                    settings.timeToCenterMax = timeToCenterMaxEdit.text.toString().toFloat()
                    settings.numberDisplayTime = numberDisplayTimeEdit.text.toString().toFloat()

                    settings.repMode = if (infiniteRepsRadio.isChecked) "infinite" else "fixed"
                    if (settings.repMode == "fixed") {
                        settings.targetReps = targetRepsEdit.text.toString().toInt()
                    }

                    var validationFailed = false
                    var errorMessage = ""

                    // Save drill-specific settings based on current drill
                    when (settings.currentDrill) {
                        "drill1" -> {
                            // Save drill 1 settings
                            settings.minShuttleNumber = minShuttleNumberEdit.text.toString().toInt()
                            settings.maxShuttleNumber = maxShuttleNumberEdit.text.toString().toInt()

                            // Validation for drill 1
                            if (settings.minShuttleNumber >= settings.maxShuttleNumber) {
                                validationFailed = true
                                errorMessage = "Min number must be less than max number"
                            }
                        }
                        "drill2" -> {
                            // Save drill 2 settings (shot probabilities)
                            val netProb = netProbEdit.text.toString().toInt()
                            val liftProb = liftProbEdit.text.toString().toInt()
                            val dropProb = dropProbEdit.text.toString().toInt()
                            val clearProb = clearProbEdit.text.toString().toInt()
                            val smashProb = smashProbEdit.text.toString().toInt()

                            // Enhanced validation for drill 2
                            // Check for negative values
                            if (netProb < 0 || liftProb < 0 || dropProb < 0 || clearProb < 0 || smashProb < 0) {
                                validationFailed = true
                                errorMessage = "Probabilities must be non-negative (0-100)"
                            }

                            // Check for values > 100
                            if (!validationFailed && (netProb > 100 || liftProb > 100 || dropProb > 100 || clearProb > 100 || smashProb > 100)) {
                                validationFailed = true
                                errorMessage = "Probabilities must be ≤ 100%"
                            }

                            // For shuttle 1 & 2 (front court) - percentages must add up to 100%
                            if (!validationFailed) {
                                val frontTotal = netProb + liftProb
                                if (frontTotal != 100) {
                                    validationFailed = true
                                    errorMessage = "Sum of Front court shots: $frontTotal% (must be 100%)"
                                }
                            }

                            // For shuttle 3 & 4 (rear court) - percentages must add up to 100%
                            if (!validationFailed) {
                                val rearTotal = dropProb + clearProb + smashProb
                                if (rearTotal != 100) {
                                    validationFailed = true
                                    errorMessage = "Sum of Rear court shots: $rearTotal% (must be 100%)"
                                }
                            }

                            if (!validationFailed) {
                                // Save probabilities
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

                    // COMMON validation (for both drills)
                    if (!validationFailed && settings.numberDisplayTime <= 0) {
                        validationFailed = true
                        errorMessage = "Display time must be positive"
                    }

                    if (!validationFailed && (settings.timeToShuttleMin > settings.timeToShuttleMax ||
                                settings.timeToCenterMin > settings.timeToCenterMax)) {
                        validationFailed = true
                        errorMessage = "Min times must be less than or equal to max times"
                    }

                    if (!validationFailed && settings.repMode == "fixed" && settings.targetReps <= 0) {
                        validationFailed = true
                        errorMessage = "Target reps must be positive"
                    }

                    if (validationFailed) {
                        // Show error but don't dismiss dialog
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        // All validation passed, save and dismiss
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
}