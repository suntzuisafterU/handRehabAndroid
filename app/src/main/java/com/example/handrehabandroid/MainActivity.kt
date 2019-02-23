package com.example.handrehabandroid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    // TODO: Why late init? What is the difference between lateinit and var ~~~ ? = null
    // Because it can be null even after being initialized
    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null

    val ACTION_USB_PERMISSION = "permission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(broadcastReceiver, filter)

        on.setOnClickListener{ sendData("2")} // inflate mode
        off.setOnClickListener{ sendData("0")} // idle mode
        disconnect.setOnClickListener{ disconnect()}
        connect.setOnClickListener{ startUsbConnecting()}
    }

    private fun startUsbConnecting() {
        //                                            optional parameter
        val usbDevices: HashMap<String, UsbDevice>? = m_usbManager.deviceList
        if (!usbDevices?.isEmpty()!!){
            var keep = true
            // iterate through all connected devices
            usbDevices.forEach{ entry ->
                m_device = entry.value
                val deviceVendorId: Int? = m_device?.vendorId
                Log.i("serial", "vendorId: $deviceVendorId")
                //              vendorId for Arduino Uno (Nano was vendorId == 1027)
                // TODO: IMPORTANT: This vendorId is a guess.  Requires testing
                if (deviceVendorId == 0x2341) {
                    val intent: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                    m_usbManager.requestPermission(m_device, intent)
                    keep = false
                    Log.i("serial", "connection successful")
                } else {
                    m_connection = null
                    m_device = null
                    Log.i("serial", "unable to connect")
                }
                if (!keep){
                    return
                }
            }
        } else {
            Log.i("serial", "no usb device connected")
        }
    }

    private fun sendData(input: String) {
        m_serial?.write(input.toByteArray())
        Log.i("Serial", "sending data: " + input.toByteArray())
    }

    private fun disconnect(){
        m_serial?.close()
    }

    // Check if the app has permission to use the usb
    // Then check for serial connecting??
    // baud rate etc.
    // val: read only variable
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // explicitly not null
            if (intent?.action!! == ACTION_USB_PERMISSION){
                val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted){
                    m_connection = m_usbManager.openDevice(m_device)
                    m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if (m_serial != null){
                        if(m_serial!!.open()){
                            m_serial!!.setBaudRate(9600)
                            // TODO: What do each of these settings mean?
                            // TODO: We are only writing to serial right now.
                            m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            m_serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            m_serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                            m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                        }
                        // if serial is closed, close the window??
                        else {
                            Log.i("Serial", "Port is closed")
                        }
                    } else {
                        Log.i("Serial", "port is null")
                    }
                } else {
                    Log.i("Serial", "permission not granted")
                }
            } else if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED){
                // If someone has actually plugged into the usb port
                startUsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED){
                // Someone has disconnected from the device
                disconnect()
            }
        }
    }
}
