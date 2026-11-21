# ZAVI AI

Premium dark Android AI assistant starter.

## Included
- Jetpack Compose chat UI with neon styling
- Telugu/English system prompt
- OpenRouter chat-completions connection
- API key entry stored with AndroidX EncryptedSharedPreferences
- GitHub Actions debug APK build

## Build
GitHub Actions builds a debug APK on push to main/master or manual workflow dispatch and uploads it as an artifact.

## API key
Open ZAVI AI > Settings and paste your OpenRouter key. Never commit API keys or place them in source code.

## Scope note
This is an integrated initial app foundation, not yet the entire long-term assistant feature set. Persistent chat history, speech input/output, offline LLM, wake word, multi-agent tools, and device automation still need implementation and testing. Sensitive device actions must require explicit confirmation.
