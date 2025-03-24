package com.forsyth.novm

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import java.io.Serializable

data class TestSerializable(val data: Int): Serializable
class TestParcelable(val data: Int) : Parcelable {
    constructor(`in`: Parcel) : this(`in`.readInt())
    override fun describeContents(): Int {
        return 0
    }
    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeInt(data)
    }
    companion object CREATOR: Parcelable.Creator<TestParcelable?> {
        override fun createFromParcel(`in`: Parcel): TestParcelable? {
            return TestParcelable(`in`)
        }
        override fun newArray(size: Int): Array<TestParcelable?> {
            return arrayOfNulls(size)
        }
    }
}
class BundleSupportedTypesActivity : StateSavingActivity() {
    // TODO IBinder
    //@Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    //var binder: IBinder = ...

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var bundle: Bundle

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var byte: Byte = 0x00.toByte() // value change to 0xFF in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var byteArray: ByteArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var ch: Char = 'a' // value change to 'b' in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var chArr: CharArray

    // TODO CharSequence, CharSequenceArray, CharSequencyArrayList

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var float: Float = 1f // value change to 2f in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var floatArray: FloatArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var intArray: IntArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var integerArrayList: ArrayList<Int>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var parcelable: Parcelable

    // TODO ParcelableArray, ParcelableArrayList

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var serializable: Serializable

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var short: Short = 1 // value change to 2 in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var shortArr: ShortArray

    // TODO Size, SizeF
    // TODO SparseParcelableArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var stringArray: Array<String>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var stringArrayList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bundle_supported_types)
        if (counter == 0) {
            bundle = Bundle().also { it.putString("foo", "bar") }
            byte = 0xFF.toByte()
            byteArray = byteArrayOf(0xFF.toByte())
            ch = 'b'
            chArr = charArrayOf('b')
            float = 2f
            floatArray = floatArrayOf(2f)
            intArray = intArrayOf(2)
            integerArrayList = arrayListOf(2)
            parcelable = TestParcelable(2)
            serializable = TestSerializable(2)
            short = 2
            shortArr = shortArrayOf(2)
            stringArray = arrayOf("foo")
            stringArrayList = arrayListOf("foo")
        } else {
            assert(bundle.getString("foo") == "bar")
            assert(byte == 0xFF.toByte())
            assert(byteArray[0] == 0xFF.toByte())
            assert(ch == 'b')
            assert(chArr[0] == 'b')
            assert(float == 2f)
            assert(floatArray[0] == 2f)
            assert(intArray[0] == 2)
            assert(integerArrayList[0] == 2)
            assert((parcelable as TestParcelable).data == 2)
            assert((serializable as TestSerializable).data == 2)
            assert(short == 2.toShort())
            assert(shortArr[0] == 2.toShort())
            assert(stringArray[0] == "foo")
            assert(stringArrayList[0] == "foo")
            Log.d("BSTA", "All tests passed")
        }
        counter += 1
    }
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    var counter = 0
}