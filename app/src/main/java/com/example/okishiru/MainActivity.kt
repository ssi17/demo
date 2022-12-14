package com.example.okishiru

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.example.okishiru.database.AppDatabase
import com.example.okishiru.json.Article
import com.example.okishiru.model.MainViewModel
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var navController: NavController
    private val sharedViewModel: MainViewModel by viewModels()
    var bgm: MediaPlayer = MediaPlayer()
    private lateinit var tts: TextToSpeech

    // 位置情報関係の変数
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var requestingLocationUpdates: Boolean = false

    var changeScripts: Boolean = false

    //位置情報使用の権限許可を確認
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 使用が許可された
            requestingLocationUpdates = true
        } else {
            // それでも拒否された時の対応
            val toast = Toast.makeText(
                this,
                "位置情報の取得権限を許可してください",
                Toast.LENGTH_LONG
            )
            toast.show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("RestrictedApi", "UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ナビゲーションの設定
        // 下部ナビゲーション
        val bottomNavigation: BottomNavigationView= findViewById(R.id.bottomNavigation)

        // フラグメント間の遷移
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        NavigationUI.setupWithNavController(bottomNavigation, navController)

        // 下部ナビゲーションの設定
        bottomNavigation.background = null
        bottomNavigation.menu.getItem(2).isEnabled = false

        // BGMの設定
        createBgm(1)

        // TextToSpeechの初期化
        tts = TextToSpeech(this, this)

        // JSONファイルを取得し、ViewModelへ保存
        val assetManager = resources.assets

        // Scripts.json
        val scriptsFile = assetManager.open("Scripts.json")
        var br = BufferedReader(InputStreamReader(scriptsFile))
        val scriptsArray = JSONArray(br.readText())

        // Articles.json
        val articlesFile = assetManager.open("Articles.json")
        br = BufferedReader(InputStreamReader(articlesFile))
        val articlesArray = JSONArray(br.readText())

        sharedViewModel.scriptsArray = scriptsArray
        sharedViewModel.articlesArray = articlesArray

        // データベース
        sharedViewModel.db = AppDatabase.getInstance(applicationContext)
        sharedViewModel.setting()
        sharedViewModel.getAllFlag()

        // FABで再生ボタンの処理を行う
        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener {
            sharedViewModel.changeStartFlag()
            if(sharedViewModel.startFlag) {
                fab.setImageResource(R.drawable.ic_pause)
                if(sharedViewModel.bgmFlag.value!!) {
                    bgm.start()
                }
                if(sharedViewModel.scripts.size != 0) {
                    startSpeech()
                }
            } else {
                if(sharedViewModel.bgmFlag.value!!) {
                    bgm.pause()
                }
                tts.stop()
                bgm.setVolume(1.0F, 1.0F)
                fab.setImageResource(R.drawable.ic_play)
            }
        }

        //位置情報の権限許可
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            requestingLocationUpdates = true
        }

        locationRequest = LocationRequest.create()
        locationRequest.setPriority(
            LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setFastestInterval(30000).interval = 30000

        //位置情報に変更があったら呼び出される
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for(location in locationResult.locations) {
                    //address取得
                    getAddress(location.latitude, location.longitude)
                }
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // GPS機能が有効になっていない場合、メッセージを表示
        val lm: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this,
                "位置情報機能が無効になっています",
                Toast.LENGTH_LONG).show()
        }

        // ネットワークに接続されていない場合、メッセージを表示
        val cm: ConnectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        if(capabilities == null) {
            Toast.makeText(
                this,
                "ネットワークに接続されていません",
                Toast.LENGTH_LONG).show()
        }
    }

    // 曲を切り替えながらBGMを再生
    private fun createBgm(num: Int){
        var count: Int = num
        bgm = MediaPlayer.create(this, Uri.parse("android.resource://$packageName/raw/bgm_$count"))
        // 再生状態でBGMがONならスタート
        if(sharedViewModel.startFlag && sharedViewModel.bgmFlag.value!!) {
            if(tts.isSpeaking) bgm.setVolume(0.1F, 0.1F)
            bgm.start()
        }
        // 曲が終わったときの処理
        bgm.setOnCompletionListener {
            // bgmインスタンスを一度解放
            bgm.stop()
            bgm.reset()
            bgm.release()
            count++
            if(count > 3) count = 1
            // 再帰呼び出し
            createBgm(count)
        }
    }

    // 音声読み上げ機能
    @SuppressLint("NewApi")
    fun startSpeech() {
        if(tts.isSpeaking) {
            tts.stop()
        }

        val articles = sharedViewModel.articles
        val scripts = sharedViewModel.scripts

        val list: MutableList<Article> = sharedViewModel.displayArticles.value!!

        tts.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onDone(id: String) {
                // BGMの音量を戻す
                bgm.setVolume(1.0F, 1.0F)
                // 読み上げが終わったコンテンツのリストを更新
                sharedViewModel.doneScripts.add(Integer.parseInt(id))
                if(changeScripts) {
                    changeScripts = false
                    startSpeech()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(p0: String?) {}

            override fun onStart(id: String) {
                // BGMの音量を下げる
                bgm.setVolume(0.1F, 0.1F)
                // 読み上げられているコンテンツの記事を記事リストから取得
                for(script in scripts) {
                    // 読み上げているコンテンツを探索
                    if(script.id == Integer.parseInt(id)) {
                        for(article in articles) {
                            // 記事リストの中からこのコンテンツに関する記事を探索
                            if(script.articleId.contains(article.id)) {
                                // すでに同じ記事が表示されていればリストから削除
                                if(list.contains(article)) {
                                    list.remove(article)
                                }
                                // リストに記事を追加
                                list.add(0, article)
                                // リストのサイズが10を超えていたら最後の要素を削除
                                if(list.size > 10) {
                                    list.removeAt(10)
                                }
                            }
                        }
                        break
                    }
                }
                // 画面に表示する記事のリストを更新
                sharedViewModel.setDisplayArticles(list)
            }
        })

        for(script in sharedViewModel.scripts) {
            // 読み上げが終わっているコンテンツなら処理をスキップ
            if(sharedViewModel.doneScripts.contains(script.id)) {
                continue
            }
            // 音声データの取得
            val text = script.voice
            // 音声読み上げのキューに追加
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "${script.id}")
        }
    }

    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS) {
            tts.let { tts ->
                val locale = Locale.getDefault()
                if(tts.isLanguageAvailable(locale) > TextToSpeech.LANG_AVAILABLE) {
                    tts.language = locale
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

    fun ttsState(): Boolean {
        return tts.isSpeaking
    }

    //緯度経度をもとに住所の取得
    private fun getAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(this)
        val address = geocoder.getFromLocation(lat, lng, 1)

        val city = address[0].locality.toString()

        if(sharedViewModel.city != city) {
            sharedViewModel.city = city
            sharedViewModel.getScripts()
            if(sharedViewModel.startFlag) {
                changeScripts = true
            }
        }
    }

    // -----位置情報-----
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onResume() {
        super.onResume()
        if(requestingLocationUpdates) {
            startLocationUpdates()
        }
    }
    // -----位置情報-----
}