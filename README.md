# novm ![build](https://github.com/foooorsyth/novm/actions/workflows/build.yml/badge.svg) ![sonatype](https://maven-badges.herokuapp.com/sonatype-central/com.forsyth.novm/novm-runtime/badge.svg)
*ditch your ViewModels*

### Quick Start

First, [add the ksp plugin to your project](https://developer.android.com/build/migrate-to-ksp#add-ksp). 
Then, add the novm runtime and compiler to your module's build.gradle.kts file:
```kotlin
val novm_version = "0.8.0"
implementation("com.forsyth.novm:novm-runtime:$novm_version")
ksp("com.forsyth.novm:novm-compiler:$novm_version")
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
See [the docs](https://developer.android.com/topic/libraries/architecture/saving-states#onsaveinstancestate) for more guidance on
state retention in the event of process death.

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
    arg("isDependency", "true")
}
```
