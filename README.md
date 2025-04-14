# novm ![build](https://github.com/foooorsyth/novm/actions/workflows/build.yml/badge.svg) ![novm-core](https://img.shields.io/maven-central/v/com.forsyth.novm/novm-core?style=flat&label=novm-core&color=%236650a4&cacheSeconds=3600&link=https%3A%2F%2Fcentral.sonatype.com%2Fartifact%2Fcom.forsyth.novm%2Fnovm-core) ![novm-runtime](https://img.shields.io/maven-central/v/com.forsyth.novm/novm-runtime?style=flat&label=novm-runtime&color=blue&cacheSeconds=3600&link=https%3A%2F%2Fcentral.sonatype.com%2Fartifact%2Fcom.forsyth.novm%2Fnovm-runtime) ![novm-compiler](https://img.shields.io/maven-central/v/com.forsyth.novm/novm-compiler?style=flat&label=novm-compiler&color=orange&cacheSeconds=3600&link=https%3A%2F%2Fcentral.sonatype.com%2Fartifact%2Fcom.forsyth.novm%2Fnovm-compiler) ![novm-compose](https://img.shields.io/maven-central/v/com.forsyth.novm/novm-compose?style=flat&label=novm-compose&color=%23fcc603&cacheSeconds=3600&link=https%3A%2F%2Fcentral.sonatype.com%2Fartifact%2Fcom.forsyth.novm%2Fnovm-compose) [![Follow @foooorsyth](https://img.shields.io/twitter/follow/foooorsyth?style=social)](https://x.com/foooorsyth)

*ditch your ViewModels*

### Quick Start

First, [add the ksp plugin to your project](https://developer.android.com/build/migrate-to-ksp#add-ksp). 
Then, add the novm runtime and compiler to your module's build.gradle.kts file. Optionally, add 
novm-compose for Compose support:
```kotlin
dependencies {
    val novm_version = "1.3.0"
    implementation("com.forsyth.novm:novm-core:$novm_version")
    implementation("com.forsyth.novm:novm-runtime:$novm_version")
    ksp("com.forsyth.novm:novm-compiler:$novm_version")
    // optional, for compose support
    implementation("com.forsyth.novm:novm-compose:$novm_version")
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
    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    lateinit var largeImage: Bitmap 

    // computedHash will survive system-initiated process death
    // see: https://developer.android.com/guide/components/activities/activity-lifecycle#asem
    @Retain(across = StateDestroyingEvent.PROCESS_DEATH)
    lateinit var computedHash: String
    // ...
}
```

State that is designated to be retained across process death must be of a type supported by [Bundle](https://developer.android.com/reference/android/os/Bundle). 
See the [chart below](#Supported-types-for-retention-across-process-death) for supported types. See [the docs](https://developer.android.com/topic/libraries/architecture/saving-states#onsaveinstancestate) for more guidance on
state retention in the event of process death.

All variables annotated with ```@Retain``` must be have ```public``` visibility.

### Fragment support

```kotlin
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent
import com.forsyth.novm.StateSavingFragment
class SomeFragment : StateSavingFragment() {
    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    lateinit var largeImage: Bitmap

    @Retain(across = StateDestroyingEvent.PROCESS_DEATH)
    lateinit var computedHash: String

    // Optional override, see below
    override var identificationStrategy = FragmentIdentificationStrategy.BY_ID
    // ...
}
```

Fragments are identified after recreation based on their ```identificationStrategy```:

```FragmentIdentificationStrategy.BY_TAG``` (default): Fragments are identified by their unique ```tag``` (you must give each of your Fragments using ```@Retain``` a unique tag using this setting)

```FragmentIdentificationStrategy.BY_ID```: Fragments are identified by their ```id```

```FragmentIdentificationStrategy.BY_CLASS```: Fragments are identified by their class

### Compose support

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.forsyth.novm.StateSavingActivity
import com.forsyth.novm.compose.setContent
import com.forsyth.novm.compose.retainAcrossRecomposition
import com.forsyth.novm.compose.retainAcrossConfigChange
import com.forsyth.novm.compose.retainAcrossProcessDeath

// must extend StateSavingActivity
class ComposeActivity : StateSavingActivity() { 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // new entry point! this is not `androidx.activity.compose.setContent`
        setContent {
            // survives recomposition -- equivalent to `remember`
            var foo by retainAcrossRecomposition { mutableIntStateOf(1) }
            // survives config change (can be `Any?`) without a ViewModel
            var bar by retainAcrossConfigChange { mutableStateOf(NonSerializableClass()) }
            // survives process death (must be Bundle type) -- equivalent to `rememberSaveable`
            var baz by retainAcrossProcessDeath { mutableStateOf(SerializableClass()) }
            // ... 
        }
        // ...
    }
    // ...
}
```

Additionally, [MutableState](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableState) 
can always be declared in your component scope (Activities & Fragments), annotated with ```@Retain```, and passed into your Compose composition.

### Coroutine support

```StateSavingActivity``` offers a ```retainedScope``` field, which is a ```CoroutineScope``` that will
survive configuration change. Computation spanning multiple configuration changes can be safely executed 
here. By default, execution occurs on ```Dispatchers.Main``` -- to execute in the background, you can use 
```withContext(Dispatchers.IO) { ... }```. ```retainedScope```'s lifetime is effectively the same as a 
```viewModelScope``` from a ```ViewModel``` in ```StateSavingActivity```'s ```ViewModelStore```.

### Multi-module support

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

### R8 / ProGuard

novm should work out of the box with code minification and shrinking enabled. novm does **not** use 
runtime reflection to save and restore state, but does need to identify certain classes by name 
at build time. The following rules in ```:novm-runtime/consumer-rules.pro``` will be inherited 
by consumers:

```
-keep class com.forsyth.novm.** { *; }
-keep class androidx.activity.ComponentActivity { *; }
-keep class androidx.fragment.app.Fragment { *; }
```

### Reporting bugs

To report a bug, enable debug logging and open a GitHub issue with a trace

```kotlin
ksp {
    arg("novm.debugLogging", "true")
}
```