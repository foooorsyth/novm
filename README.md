# novm ![build](https://github.com/foooorsyth/novm/actions/workflows/build.yml/badge.svg) ![sonatype](https://maven-badges.herokuapp.com/sonatype-central/com.forsyth.novm/novm-runtime/badge.svg)
*ditch your ViewModels*

### Quick Start

First, [add the ksp plugin to your project](https://developer.android.com/build/migrate-to-ksp#add-ksp). 
Then, add the novm runtime and compiler to your module's build.gradle.kts file:
```kotlin
dependencies {
    val novm_version = "0.9.0"
    implementation("com.forsyth.novm:novm-runtime:$novm_version")
    ksp("com.forsyth.novm:novm-compiler:$novm_version")
}
```

Extend ```StateSavingActivity```, declare state directly in your Activity, and annotate it with ```@Retain```

```kotlin
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent
import com.forsyth.novm.StateSavingActivity
class MainActivity : StateSavingActivity() {
    // largeImage will survive configuration change
    // see: https://developer.android.com/guide/topics/resources/runtime-changes
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    lateinit var largeImage: Bitmap 

    // computedHash will survive system-initiated process death
    // see: https://developer.android.com/guide/components/activities/activity-lifecycle#asem
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var computedHash: String
    // ...
}
```

State that is designated to be retained across process death must be of a type supported by [Bundle](https://developer.android.com/reference/android/os/Bundle). 
See the [chart below](#Supported-types-for-retention-across-process-death) for supported types. See [the docs](https://developer.android.com/topic/libraries/architecture/saving-states#onsaveinstancestate) for more guidance on
state retention in the event of process death.

All variables annotated with @Retain must be have ```public``` visibility.

### Fragment support

```kotlin
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent
import com.forsyth.novm.StateSavingFragment
class SomeFragment : StateSavingFragment() {
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    lateinit var largeImage: Bitmap

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var computedHash: String

    // Optional override, see below
    override var identificationStrategy = FragmentIdentificationStrategy.ID
    // ...
}
```

Fragments are identified after recreation based on their ```identificationStrategy```:

**FragmentIdentificationStrategy.TAG (default)**: Fragments are identified by their unique ```tag``` (you must give each of your Fragments using @Retain a unique tag)

**FragmentIdentificationStrategy.ID**: Fragments are identified by their ```id```

**FragmentIdentificationStrategy.CLASS**: Fragments are identified by their class

### Multi-module Support

novm supports apps with multiple modules. Library modules must declare themselves as dependencies in their build.gradle.kts file:
```kotlin
ksp {
    arg("novm.isDependency", "true")
}
```

### Supported types for retention across process death

| Bundle Entry Type                 | Supported by novm? |
|-----------------------------------|--------------------|
| IBinder                           | :white_check_mark: |
| Bundle                            | :white_check_mark: |
| Byte                              | :white_check_mark: |
| ByteArray                         | :white_check_mark: |
| Char                              | :white_check_mark: |
| CharArray                         | :white_check_mark: |
| CharSequence                      | :white_check_mark: |
| Array\<(out) CharSequence\>       | :white_check_mark: |
| ArrayList\<CharSequence\>         | :white_check_mark: |
| Float                             | :white_check_mark: |
| FloatArray                        | :white_check_mark: |
| Int                               | :white_check_mark: |
| IntArray                          | :white_check_mark: |
| ArrayList\<Int\>                  | :white_check_mark: |
| Parcelable                        | :white_check_mark: |
| Array\<(out) Parcelable\>         | :white_check_mark: |
| ArrayList\<(out) Parcelable\>     | :white_check_mark: |
| SparseArray\<(out) Parcelable\>   | :white_check_mark: |
| PersistableBundle (via #putAll()) | :x:                |
| Serializable                      | :white_check_mark: |
| Short                             | :white_check_mark: |
| ShortArray                        | :white_check_mark: |
| Size                              | :white_check_mark: |
| SizeF                             | :white_check_mark: |
| String                            | :white_check_mark: |
| Array\<(out) String\>             | :white_check_mark: |
| ArrayList\<String\>               | :white_check_mark: |

### Working with R8 / ProGuard

novm should work out of the box with code minification and shrinking enabled. novm does not use 
runtime reflection to save and restore state, but does need to identify certain classes by name 
at build time. The following rules in ```:novm-runtime/consumer-rules.pro``` will be inherited 
by consumers:

```
-keep class com.forsyth.novm.** { *; }
-keep class androidx.activity.ComponentActivity { *; }
-keep class androidx.fragment.app.Fragment { *; }
```