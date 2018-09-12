# MediaCompressor
Compress video and photo
[![API](https://img.shields.io/badge/API-17%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=17)
[![](https://jitpack.io/v/ali-sardari/MediaCompressor.svg)](https://jitpack.io/#ali-sardari/MediaCompressor)

Usages
Use this dependency in your build.gradle file to reference this library in your project

Step 1. Add the JitPack repository to your build file. Add it in your root build.gradle at the end of repositories:

```groovy
allprojects {
        repositories {
            maven { url "https://jitpack.io" }
        }
    }
```

Step 2. Add the dependency
```groovy
dependencies {
    implementation 'com.github.ali-sardari:MediaCompressor:1.0.0-beta'
}
```

Then in your Java Code, you use it like below.

```java
MediaCompressor.compressVideo(sourceFilePath, destFilePath, 640, 480, new MediaCompressor.IMediaCompressor() {
  @Override
  public void success() {
      File file = new File(destFilePath);
      float length = file.length() / 1024f; // Size in KB
      String value;

      if (length >= 1024)
          value = length / 1024f + " MB";
      else
          value = length + " KB";

      String text = String.format(Locale.US, "%s\nName: %s\nSize: %s", getString(R.string.video_compression_complete), file.getName(), value);

      Log.i("TAG", "success-> :" +text);

      addVideoToGallery(file);
  }

  @Override
  public void failed() {

  }

  @Override
  public void progress(float progress) {

  }
});
```
