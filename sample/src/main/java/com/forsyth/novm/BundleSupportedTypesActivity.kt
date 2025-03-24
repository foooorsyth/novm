package com.forsyth.novm

import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import java.io.FileDescriptor
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
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var binder: IBinder

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

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var charSeq: CharSequence

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var charSeqArr: Array<CharSequence>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var charSeqArrList: ArrayList<CharSequence>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var float: Float = 1f // value change to 2f in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var floatArray: FloatArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var int: Int = 1
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var intArray: IntArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var integerArrayList: ArrayList<Int>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var parcelable: Parcelable

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var parcelableArray: Array<TestParcelable>

    // TODO ParcelableArray, ParcelableArrayList
    // TODO SparseParcelableArray
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var serializable: Serializable

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var short: Short = 1 // value change to 2 in first onCreate

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var shortArr: ShortArray

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var size: Size

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var sizef: SizeF

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var str: String

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var stringArray: Array<String>

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    lateinit var stringArrayList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bundle_supported_types)
        if (counter == 0) {
            binder = newBinder()
            bundle = Bundle().also { it.putString("foo", "bar") }
            byte = 0xFF.toByte()
            byteArray = byteArrayOf(0xFF.toByte())
            ch = 'b'
            chArr = charArrayOf('b')
            charSeq = "foo"
            charSeqArr = arrayOf("foo")
            charSeqArrList = arrayListOf("foo")
            float = 2f
            floatArray = floatArrayOf(2f)
            int = 2
            intArray = intArrayOf(2)
            integerArrayList = arrayListOf(2)
            parcelable = TestParcelable(2)
            //parcelableArray = arrayOf(TestParcelable(2))
            serializable = TestSerializable(2)
            short = 2
            shortArr = shortArrayOf(2)
            size = Size(2, 2)
            sizef = SizeF(2f, 2f)
            str = "foo"
            stringArray = arrayOf("foo")
            stringArrayList = arrayListOf("foo")
        } else {
            assert(binder.interfaceDescriptor == "foo")
            assert(bundle.getString("foo") == "bar")
            assert(byte == 0xFF.toByte())
            assert(byteArray[0] == 0xFF.toByte())
            assert(ch == 'b')
            assert(chArr[0] == 'b')
            assert(charSeq == "foo")
            assert(charSeqArr[0] == "foo")
            assert(charSeqArrList[0] == "foo")
            assert(float == 2f)
            assert(floatArray[0] == 2f)
            assert(int == 2)
            assert(intArray[0] == 2)
            assert(integerArrayList[0] == 2)
            assert((parcelable as TestParcelable).data == 2)
            //assert(parcelableArray[0].data == 2)
            assert((serializable as TestSerializable).data == 2)
            assert(short == 2.toShort())
            assert(shortArr[0] == 2.toShort())
            assert(size.width == 2 && size.height == 2)
            assert(sizef.width == 2f && sizef.height == 2f)
            assert(str == "foo")
            assert(stringArray[0] == "foo")
            assert(stringArrayList[0] == "foo")
            Log.d("BSTA", "All tests passed")
        }
        counter += 1
    }
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    var counter = 0

    private fun newBinder(): IBinder {
        return object: IBinder {
            override fun getInterfaceDescriptor(): String {
                return "foo"
            }
            override fun pingBinder(): Boolean {
                return true
            }
            override fun isBinderAlive(): Boolean {
                return true
            }
            override fun queryLocalInterface(descriptor: String): IInterface? {
                return null
            }
            override fun dump(fd: FileDescriptor, args: Array<out String>?) { }
            override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) { }
            override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                return true
            }
            override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { }
            override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
                return true
            }
        }
    }
}