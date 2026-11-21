# ZAVI AI

A free-first Android AI assistant prototype built with Kotlin and Jetpack Compose.

## Current integration branch

Branch: `integration/two-phase-zavi`

### Phase 1 foundation now included
- Modular local agent router: General, Research, Finance, Market, Brand, Marketing and Schemes.
- Agent workflow prompt/state builder inspired by MAYA's routing and graph-state approach.
- OpenRouter chat client with `openrouter/free` as the default model route.
- Telugu / Telugu transliteration / English prompt handling.
- Local encrypted conversation history and user-approved memory context.
- Tap-to-talk SpeechRecognizer input and Android TextToSpeech replies.
- OpenRouter API key and model configuration stored with AndroidX EncryptedSharedPreferences.
- One-tap sharing of the current chat through Android's share sheet.
- Separate control to clear saved memory without deleting chat history.
- Improved Telugu voice-input recovery with clearer Android speech-service error messages.
- Existing premium dark neon Compose UI retained.

### Phase 2 planned
- Wake-word / foreground voice mode with clear microphone notification and stop control.
- Floating assistant bubble.
- Permission-gated Android actions: dialer, messaging intents, app launch, torch, volume and settings panels.
- Camera capture and OCR.
- Reminders, calendar, routines and navigation shortcuts.
- Optional web research/news/weather integrations with clearly disclosed provider limits.
- More advanced local intent recognition and tool workflows.

## Build
GitHub Actions builds the debug APK on push to main/master or manual workflow dispatch. The integration branch can be built by selecting it in the Actions UI after the branch is pushed.

## API key
Open ZAVI AI > Settings and paste your OpenRouter API key. Never commit API keys or paste them into chat.

The default model is `openrouter/free`, which routes to currently available free models. Free routing is subject to provider availability and rate limits; choosing a paid model may incur charges.

## Privacy and safety
- API keys, saved chat history and user-approved memory are kept in encrypted app preferences on the device.
- Voice input starts only after the user taps the microphone and grants Android microphone permission.
- No always-on microphone is implemented in this phase.
- Sensitive phone actions in Phase 2 must use Android permission flows and explicit user confirmation.
- No silent message sending, hidden recording or undisclosed cloud upload is intended.

## Reference and licensing notes
- Kenji (https://github.com/001kenji/Assistant-AI-Code) is licensed under PolyForm Noncommercial 1.0.0. Its license and required notice must be observed for any permitted reuse. No Kenji source file or model asset is copied into this branch.
- MAYA AI (https://github.com/dev-infinity101/MAYA-AI) did not expose a LICENSE file in the inspected default-branch tree. Its source code is therefore not copied. ZAVI independently implements general agent-routing and workflow concepts.
- ZAVI's Kotlin implementation is written for this app rather than importing either project as a dependency.

## Scope
This is a staged implementation. Phase 1 is a working foundation, not the finished two-phase assistant. Research routing currently changes the agent instructions; it does not yet perform live web search. Phase 2 will add phone tools and external services incrementally, subject to Android OS restrictions and provider availability.

## Untouched upstream references

The reference repositories are pinned as Git submodules. Their source files are not modified in this ZAVI branch.

Initialize the pinned sources after cloning:

```bash
git submodule update --init --recursive
```

Pinned upstream commits:
- Kenji: `001kenji/Assistant-AI-Code@fafcbdf9374738c588624a8e38bd67a9c68db8e9`
- MAYA AI: `dev-infinity101/MAYA-AI@d296d5cae6384cbdf78bb3bcdaf850dcd3129fa8`

License boundary:
- Kenji declares PolyForm Noncommercial 1.0.0. Any permitted use must retain its required copyright notice and comply with the non-commercial terms. Commercial use requires written permission from its copyright holder.
- The inspected MAYA AI default branch did not contain a LICENSE file. It is linked only as an untouched reference; do not copy, modify, distribute or execute its code in ZAVI until the author provides permission or a license.

ZAVI's own adapter and integration files live outside the submodules. They are separately written and do not alter either upstream repository. The upstream submodules are not yet compiled into the ZAVI APK as a single Android module: Kenji is a standalone Android app project, while MAYA is a Python backend with external database and service requirements. The bridge between them remains a separate integration task.


## All-in-One upgrade cycle (integration branch)

- **Web Research:** current DuckDuckGo HTML retrieval is used only when the request calls for live information; successfully retrieved HTTP(S) sources are appended as a source list. Search availability depends on DuckDuckGo access and is not guaranteed.
- **Voice:** tap-to-talk Telugu speech input and optional Android TTS replies remain foreground, user-triggered. No always-listening wake word is enabled.
- **Autonomous workflow:** explicit agent-mode phrases invoke Planner → Specialist → Reviewer. Each stage uses configured cloud provider APIs and may consume additional quota.
- **Coding and Creator agents:** specialist routing supports coding/debugging and Telugu-English Shorts/Reels scripts, captions and hashtags. It does not silently edit local files or publish content.
- **Phone automation:** WorkManager reminders and confirmation-based Android intents are available. Android settings changes and message sending remain user-controlled.
- **Dashboard:** provider readiness, reminders, voice mode and quick actions are shown in the Compose workspace.
- **Telugu + English OCR:** camera images are processed with Tesseract4Android. English and Telugu language models are downloaded once into app-private storage on first OCR use; OCR then works offline. The first run requires internet and storage space. OCR output quality depends on photo clarity and font.
