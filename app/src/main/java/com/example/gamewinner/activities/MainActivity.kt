package com.example.gamewinner.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.gamewinner.databinding.ActivityMainBinding
import com.example.gamewinner.screencapture.ScreenCaptureService
import com.example.gamewinner.utils.UserPreferences

/**
 * Launch screen activity for GameWinner.
 *
 * Displays the app branding, custom prompt input, and two mode buttons:
 * - Camera Mode: launches CameraActivity for physical camera scanning
 * - Screen Mode: starts screen capture service with floating overlay
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Camera permission request launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(
                this,
                "Camera permission is required to scan questions",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // MediaProjection permission launcher
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from settings, check if permission was granted
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            Toast.makeText(
                this,
                "Overlay permission is required for Screen Mode",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue regardless — the notification is low priority
        checkOverlayPermissionAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Register MediaProjection launcher
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startScreenCaptureService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(
                    this,
                    "Screen capture permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        setupUI()
    }

    private fun setupUI() {
        // Load saved prompt and API key
        val savedPrompt = UserPreferences.getCustomPrompt(this)
        val savedApiKey = UserPreferences.getGeminiApiKey(this)
        
        binding.etCustomPrompt.setText(savedPrompt)
        binding.etApiKey.setText(savedApiKey)

        // Camera Mode button
        binding.btnStartCamera.setOnClickListener {
            saveUserInputs()
            checkCameraPermissionAndLaunch()
        }

        // Screen Mode button
        binding.btnStartScreen.setOnClickListener {
            saveUserInputs()

            if (ScreenCaptureService.isRunning()) {
                Toast.makeText(this, "Screen capture already running!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startScreenMode()
        }
    }

    private fun saveUserInputs() {
        val currentPrompt = binding.etCustomPrompt.text?.toString() ?: ""
        val currentApiKey = binding.etApiKey.text?.toString() ?: ""
        
        UserPreferences.setCustomPrompt(this, currentPrompt)
        UserPreferences.setGeminiApiKey(this, currentApiKey)
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAMERA MODE
    // ═══════════════════════════════════════════════════════════════

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(
                    this,
                    "GameWinner needs camera access to scan quiz questions",
                    Toast.LENGTH_LONG
                ).show()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    // ═══════════════════════════════════════════════════════════════
    //  SCREEN MODE
    // ═══════════════════════════════════════════════════════════════

    private fun startScreenMode() {
        // Step 1: Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        checkOverlayPermissionAndStart()
    }

    private fun checkOverlayPermissionAndStart() {
        // Step 2: Check overlay ("draw over other apps") permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please enable 'Display over other apps' for GameWinner",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        // Step 3: Request MediaProjection permission
        requestMediaProjection()
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, "Screen capture started! Tap the floating button.", Toast.LENGTH_LONG).show()

        // Minimize the app so the user can navigate to their quiz
        moveTaskToBack(true)
    }
}
