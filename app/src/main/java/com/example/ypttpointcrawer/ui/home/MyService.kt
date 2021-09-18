package com.example.ypttpointcrawer.ui.home

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ypttpointcrawer.MainActivity
import com.example.ypttpointcrawer.R
import com.example.ypttpointcrawer.ui.home.HomeFragment.Companion.CHANNEL_ID
import com.example.ypttpointcrawer.ui.home.HomeFragment.Companion.PREFERENCE_FILE
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet
import kotlin.concurrent.thread


class MyService : Service() {
    val TAG = "MyService"
    inner class MyBinder : Binder() {
        val service: MyService
            get() = this@MyService
    }

    var workerThread:Thread? = null
    var isNeedCancelThread = false
    var notificationId = 1

    //通过binder实现调用者client与Service之间的通信

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service")
            .setContentText("content Text")
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(notificationId, notification)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestory")
        isNeedCancelThread = true
        super.onDestroy()
    }

    @SuppressLint("CheckResult")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")
        val context = applicationContext
        workerThread = thread(start = true){
                val manager =  getSystemService(ALARM_SERVICE) as AlarmManager;
                val hour = 1000 * 60 * 30
                val triggerAtTime = SystemClock.elapsedRealtime() + hour;
                val i =  Intent(this, AlarmReceiver::class.java);
                val pi = PendingIntent.getBroadcast(this, 0, i, 0);
                manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pi);
                if (isNeedCancelThread || !isNetworkConnected()){
                    Log.i(TAG, "Service worker exit!")
//                    manager.cancel(pi)
                }else{
                    Log.i(TAG, "Service working")
                    Single.fromCallable {
                        val doc = Jsoup.connect("https://www.pttweb.cc/bbs/Lifeismoney").get()
                        val tagTitleList = doc.select("div.e7-right")
                        tagTitleList
                    }
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(Consumer { elements ->
                            var havePoints = false
                            var candidateElement:org.jsoup.nodes.Element? = null
                            var post = Post()
                            for (e in elements){
                                post.let {
                                    it.title = e.select("span.e7-title").select("span.e7-show-if-device-is-xs").text().lowercase()
                                    val date = e.select("div.e7-grey-text").last()?.text()?.split(",")
                                    if (date?.size == 2){
                                        it.postDate = date[1].split(" ")[1]
                                    }
                                }

                                val currentDate = SimpleDateFormat(
                                    "MM/dd",
                                    Locale.getDefault()
                                ).format(Date())
                                val isTodayPost = post.postDate.equals(currentDate)

                                if (post.title.contains("line point") && isTodayPost){
                                    havePoints = true
                                    e.let {candidateElement = it.select("span.e7-title")[0]}
                                    break
                                }
                            }
                            val pref = context.getSharedPreferences(PREFERENCE_FILE, MODE_PRIVATE)
                            val emptySet = HashSet<String>()

                            val sets:MutableSet<String> = HashSet(pref.getStringSet("Points", emptySet))
                            if (havePoints && sets.contains(post.uniqueKey()) == false){
                                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                                    .setContentTitle("Line Point!!")
                                    .setContentText(candidateElement?.text().toString())
                                    .setStyle(
                                        NotificationCompat.BigTextStyle()
                                            .bigText(post.uniqueKey()))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                                sendNotification(builder)

                                // save notification
                                sets.add(post.uniqueKey())
                                pref.edit().putStringSet("Points", sets).apply()
                            }
                        })
                }
        }
        return START_STICKY
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun startService(service: Intent?): ComponentName? {
        Log.i(TAG, "Service startService")
        return super.startService(service)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "Service onbind")
        TODO("Return the communication channel to the service.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun sendNotification(builder:androidx.core.app.NotificationCompat.Builder){
        with(NotificationManagerCompat.from(applicationContext)) {
            // notificationId is a unique int for each notification that you must define
//            notificationId +=1
            notify(notificationId, builder.build())
        }
    }
}