# novm
## *You don't need a ViewModel!*

### Usage

Add the library and symbol processor to your module's build.gradle file:
```kotlin
val novm_version = "0.8.0"
implementation("com.forsyth.novm:novm:$novm_version")
ksp("com.forsyth.novm:compiler:$novm_version")
```

Declare state with ```@Retain``` directly in your Activity and extend ```StateSavingActivity```

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

NOTE: State that is designated to survive process death must be of a type supported by [Bundle](https://developer.android.com/reference/android/os/Bundle). 
See: [https://developer.android.com/topic/libraries/architecture/saving-states#onsaveinstancestate](https://developer.android.com/topic/libraries/architecture/saving-states#onsaveinstancestate)

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
    override var identificationStrategy: FragmentIdentificationStrategy = FragmentIdentificationStrategy.ID
}
```

Fragments are identified after recreation based on their ```identificationStrategy```:

**FragmentIdentificationStrategy.TAG (default)**:
- Fragments are identified by their unique ```tag```

**FragmentIdentificationStrategy.ID**:
- Fragments are identified by their ```id```

**FragmentIdentificationStrategy.CLASS**:
- Fragments are identified by their class
