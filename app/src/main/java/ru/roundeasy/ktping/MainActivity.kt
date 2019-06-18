package ru.roundeasy.ktping

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern


class MainActivity : AppCompatActivity() {
    private val outerClass = WeakReference<MainActivity>(this)
    private var mHandlerThread = MyHandler(outerClass)

    private var mThread: Thread? = null
    private var isThreadRunning = false
    private var lastPingString = ""
    private var errorMessage = ""

    companion object {
        private const val STOP = 101
        private const val PING = 102
    }

    class MyHandler(private val outerClass: WeakReference<MainActivity>) : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == PING) {
                outerClass.get()?.updateText()
            }
            else if (msg.what == STOP) {
                outerClass.get()?.togglePing(false, on = false)
            }
        }
    }

    private fun parsePingString(s: String): Matcher {
        val re = Pattern.compile(
            "\\[([0-9.]+)\\] ([0-9]+) bytes from ([0-9.]+): icmp_seq=([0-9]+) ttl=([0-9]+) time=([0-9.]+) (\\w+)",
            Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
        return re.matcher(s)

    }

    private fun updateText() {
        val res = parsePingString(lastPingString)

        if (!res.find()) {
            return
        }
        val row = TableRow(this)
        row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT)

        val rowView = layoutInflater.inflate(R.layout.result_row, tableLayout, false)
        val tvTimestamp = rowView.findViewById(R.id.tvTimestamp) as TextView
        val tvSize = rowView.findViewById(R.id.tvSize) as TextView
        val tvTarget = rowView.findViewById(R.id.tvTarget) as TextView
        val tvSeqN = rowView.findViewById(R.id.tvSeqN) as TextView
        val tvTtl = rowView.findViewById(R.id.tvTtl) as TextView
        val tvRtt = rowView.findViewById(R.id.tvRtt) as TextView
        tvTimestamp.text = res.group(1)
        tvSize.text = res.group(2)
        tvTarget.text =res.group(3)
        tvSeqN.text = res.group(4)
        tvTtl.text = res.group(5)
        tvRtt.text = "${res.group(6)} ${res.group(7)}"
        tableLayout.addView(rowView, 1)
    }

    internal inner class PingProcess : Runnable {
        override fun run() {
            val serverAddr = textServer.text.toString()
            val size = textSize.text.toString()
            val interval = textInterval.text.toString()
            val cmd = mutableListOf("ping", "-D")
            if (size != "") {
                cmd.add("-s")
                cmd.add(size)
            }
            if (interval != "") {
                cmd.add("-i")
                cmd.add(interval)
            }
            cmd.add(serverAddr)

            val process = Runtime.getRuntime().exec(cmd.toTypedArray())

            val stdInput = BufferedReader(InputStreamReader(process.inputStream))
            for (l in stdInput.lines()) {
                if (!isThreadRunning) {
                    break
                }

                try {
                    Thread.sleep(100)
                } catch (ex: InterruptedException) {
                    ex.printStackTrace()
                }

                lastPingString = l

                val message = Message()
                message.what = PING
                mHandlerThread.sendMessage(message)
            }
            if (isThreadRunning) {
                val errOutput = BufferedReader(InputStreamReader(process.errorStream))
                errorMessage = errOutput.readLine()

                val message = Message()
                message.what = STOP
                mHandlerThread.sendMessage(message)
            }
            process.destroy()
        }
    }

    private fun togglePing(toggle: Boolean = true, on: Boolean? = null) {
        var doEnable = (on != null && on)
        if (toggle) {
            doEnable = (mThread == null)
        }

        if (errorMessage != "") {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }

        isThreadRunning = doEnable
        if (!doEnable) {
            if (mThread!!.isAlive) {
                mThread?.join()
            }
            mThread = null
            button.text = resources.getString(R.string.btn_start)
        } else {
            mThread = Thread(PingProcess())
            mThread?.start()
            button.text = resources.getString(R.string.btn_stop)
        }
        errorMessage = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val headerRow = layoutInflater.inflate(R.layout.result_row, tableLayout, false)
        tableLayout.addView(headerRow)

        button.setOnClickListener {
            togglePing()
        }
    }
}
