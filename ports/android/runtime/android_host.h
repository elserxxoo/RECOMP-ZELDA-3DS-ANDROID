#pragma once

#include <atomic>
#include <cstdint>
#include <string>

struct AndroidOverlayInputState {
  std::atomic<uint32_t> buttons{0};
  std::atomic<float> circlePadX{0.0f};
  std::atomic<float> circlePadY{0.0f};
  std::atomic<float> cStickX{0.0f};
  std::atomic<float> cStickY{0.0f};
  std::atomic<float> touchX{0.0f};
  std::atomic<float> touchY{0.0f};
  std::atomic<bool> touchPressed{false};
  std::atomic<bool> touchEnabled{false};
  std::atomic<bool> swapScreens{false};
  // True while a secondary-display surface is attached (dual-screen mode).
  std::atomic<bool> secondaryDisplayActive{false};
  // Touch on the secondary display (3DS bottom screen), in secondary-view pixels.
  std::atomic<bool> secondaryTouchPressed{false};
  std::atomic<float> secondaryTouchX{0.0F};
  std::atomic<float> secondaryTouchY{0.0F};
  std::atomic<uint32_t> secondaryTouchW{0U};
  std::atomic<uint32_t> secondaryTouchH{0U};
};

namespace Oot3dNativeGame {
class TopScreenUiConfigRuntime;
}

AndroidOverlayInputState &GetAndroidOverlayInputState();

void InitializeAndroidGameHost();
void ShutdownAndroidGameHost();
void SetAndroidStoragePath(const std::string &path);
const std::string &GetAndroidStoragePath();
int RunOot3dNativeGameMain(int argc, char** argv);

void RegisterAndroidTopScreenConfigRuntime(
    std::shared_ptr<Oot3dNativeGame::TopScreenUiConfigRuntime> runtime);
void NotifyAndroidTopScreenConfigChanged();

#ifdef __cplusplus
extern "C" {
#endif
void RecordAndroidGamePresentationFrame();
float GetAndroidGameFps();
#ifdef __cplusplus
}
#endif
