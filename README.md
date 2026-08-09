# GameWinner 🏆

GameWinner is a powerful, real-time Android AI assistant designed to help you quickly solve multiple-choice quizzes and trivia questions on the fly. By combining **Google ML Kit** for rapid on-device text recognition (OCR) and **Google's Gemini AI** for advanced reasoning, GameWinner seamlessly reads the questions on your screen or through your camera and immediately returns the correct answer.

## ✨ Features

- **📱 Screen Mode (Floating Overlay)**
  GameWinner runs quietly in the background, displaying a small floating capture bubble over your other apps. Tap the bubble while in a quiz, and GameWinner will silently capture the screen, read the question, and pop up a resizable, draggable, and minimizable card with the correct answer and a brief explanation.
  
- **📷 Camera Mode**
  Taking a quiz on a separate device or paper? Point your camera at the question. GameWinner will use a high-res capture to extract the text and overlay the AI's answer right on your camera viewfinder.

- **🤖 Google Gemini AI Integration**
  Uses the power of the Gemini API to parse complex questions and deduce the best answer among choices, returning highly accurate results with confidence levels.

- **⚙️ Custom Prompts & Bring-Your-Own-Key**
  Securely enter your own Gemini API Key directly in the app. You can also specify custom prompt instructions (e.g., "Answer in Spanish", or "Act as an expert historian") to tailor the AI's output exactly to your needs.

- **⚡ Blazing Fast On-Device OCR**
  Utilizes Google's ML Kit Vision API for instant offline text extraction from images and screen captures, passing only clean text to the AI to save data and speed up response times.

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Android Views (XML layouts, WindowManager Overlays)
- **Screen Capture**: Android `MediaProjection` API & `VirtualDisplay`
- **Camera Capture**: AndroidX CameraX
- **OCR (Optical Character Recognition)**: Google ML Kit Text Recognition API
- **Networking**: Retrofit 2 & Gson
- **AI Processing**: Google Gemini API (`v1beta/models/gemini-1.5-flash:generateContent`)

## 🚀 Getting Started

### Prerequisites
- Android Studio (Koala or newer recommended)
- An Android device running Android 8.0 (API 26) or higher.
- A **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/NaradaWickramasinghe/GameWinner.git
   ```
2. Open the project in Android Studio.
3. Build and run the app on your physical device (`./gradlew installDebug`). 
*(Note: Screen Mode overlays and Camera capture work best on physical hardware rather than emulators).*
4. Launch the app, enter your Gemini API Key in the home screen, and choose either Camera Mode or Screen Mode to start winning!

## 🔒 Permissions Used
- **Camera**: Required for Camera Mode scanning.
- **Display over other apps**: Required for the Screen Mode floating bubble and answer overlay.
- **Screen Recording (MediaProjection)**: Required for capturing the screen in Screen Mode (prompts the user for permission every time the mode is launched for privacy).

## 📝 License

This project is open-sourced under the MIT License.
