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

data class TrainingSettings(
    var timeToShuttleMin: Float = 1.0f,
    var timeToShuttleMax: Float = 3.0f,
    var timeToCenterMin: Float = 1.0f,
    var timeToCenterMax: Float = 2.0f,
    var repMode: String = "infinite", // "infinite" or "fixed"
    var targetReps: Int = 10,
    var soundEnabled: Boolean = true,

    // NEW FIELDS:
    var minShuttleNumber: Int = 1,    // Minimum shuttle number (e.g., 1)
    var maxShuttleNumber: Int = 4,    // Maximum shuttle number (e.g., 6)
    var numberDisplayTime: Float = 0.5f  // Time to display number in seconds (e.g., 1.0)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()
        initializeViews()
        loadSettings()
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
        timerBar1 = findViewById(R.id.timerBar1)
        timerBar2 = findViewById(R.id.timerBar2)
        timerText1 = findViewById(R.id.timerText1)
        timerText2 = findViewById(R.id.timerText2)
        repCounterText = findViewById(R.id.repCounterText)
        startStopButton = findViewById(R.id.startStopButton)
        settingsButton = findViewById(R.id.settingsButton)
        soundToggle = findViewById(R.id.soundToggle)

        // Set initial states
        updateDisplay()
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
        shuttleNumber = (settings.minShuttleNumber..settings.maxShuttleNumber).random()
        shuttleNumberText.text = shuttleNumber.toString()

        // HIDE the status text
        countdownStatus.visibility = View.GONE
        shuttleNumberText.visibility = View.VISIBLE

        if (settings.soundEnabled) {
            speak(shuttleNumber.toString())
        }

        // Show shuttle number for configured time
        shuttleTimerHandler = Handler(Looper.getMainLooper())
        shuttleTimerRunnable = Runnable {
            shuttleNumberText.visibility = View.GONE
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
            }
            Phase.TIME_TO_SHUTTLE -> {
                countdownText.visibility = View.GONE
                shuttleNumberText.visibility = View.GONE
                timerBar2.visibility = View.GONE
                timerText2.visibility = View.GONE
            }
            Phase.TIME_TO_CENTER -> {
                countdownText.visibility = View.GONE
                shuttleNumberText.visibility = View.GONE
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

        // NEW: Find the new shuttle number fields
        val minShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.minShuttleNumberEdit)
        val maxShuttleNumberEdit = dialogView.findViewById<EditText>(R.id.maxShuttleNumberEdit)
        val numberDisplayTimeEdit = dialogView.findViewById<EditText>(R.id.numberDisplayTimeEdit)

        // Set current values
        timeToShuttleMinEdit.setText(settings.timeToShuttleMin.toString())
        timeToShuttleMaxEdit.setText(settings.timeToShuttleMax.toString())
        timeToCenterMinEdit.setText(settings.timeToCenterMin.toString())
        timeToCenterMaxEdit.setText(settings.timeToCenterMax.toString())
        targetRepsEdit.setText(settings.targetReps.toString())

        minShuttleNumberEdit.setText(settings.minShuttleNumber.toString())
        maxShuttleNumberEdit.setText(settings.maxShuttleNumber.toString())
        numberDisplayTimeEdit.setText(settings.numberDisplayTime.toString())

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

        AlertDialog.Builder(this)
            .setTitle("Training Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, which ->
                try {
                    settings.timeToShuttleMin = timeToShuttleMinEdit.text.toString().toFloat()
                    settings.timeToShuttleMax = timeToShuttleMaxEdit.text.toString().toFloat()
                    settings.timeToCenterMin = timeToCenterMinEdit.text.toString().toFloat()
                    settings.timeToCenterMax = timeToCenterMaxEdit.text.toString().toFloat()

                    // NEW: Save shuttle number settings
                    settings.minShuttleNumber = minShuttleNumberEdit.text.toString().toInt()
                    settings.maxShuttleNumber = maxShuttleNumberEdit.text.toString().toInt()
                    settings.numberDisplayTime = numberDisplayTimeEdit.text.toString().toFloat()

                    settings.repMode = if (infiniteRepsRadio.isChecked) "infinite" else "fixed"
                    if (settings.repMode == "fixed") {
                        settings.targetReps = targetRepsEdit.text.toString().toInt()
                    }

                    // Validation
                    if (settings.minShuttleNumber >= settings.maxShuttleNumber) {
                        Toast.makeText(this, "Min number must be less than max number", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    if (settings.numberDisplayTime <= 0) {
                        Toast.makeText(this, "Display time must be positive", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    saveSettings()
                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid input values", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
}