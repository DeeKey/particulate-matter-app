/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.ui.activity

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognizerIntent
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.chillibits.pmapp.model.Sensor
import com.chillibits.pmapp.network.ServerMessagingUtils
import com.chillibits.pmapp.network.handleServerInfo
import com.chillibits.pmapp.network.loadServerInfo
import com.chillibits.pmapp.service.SyncJobService
import com.chillibits.pmapp.service.WebRealtimeSyncService
import com.chillibits.pmapp.storage.AppDatabase
import com.chillibits.pmapp.tool.*
import com.chillibits.pmapp.ui.adapter.recyclerview.SensorAdapter
import com.chillibits.pmapp.ui.adapter.viewpager.ViewPagerAdapterMain
import com.chillibits.pmapp.ui.fragment.restartApp
import com.chillibits.pmapp.ui.view.PlacesSearchDialog
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog
import com.google.android.libraries.places.api.model.Place
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.miguelcatalan.materialsearchview.MaterialSearchView
import com.mrgames13.jimdo.feinstaubapp.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_import_export.view.*
import kotlinx.android.synthetic.main.place_search_dialog.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivity : AppCompatActivity(), PlacesSearchDialog.PlaceSelectedCallback {

    // Variables as objects
    lateinit var pagerAdapter: ViewPagerAdapterMain
    private var prevMenuItem: MenuItem? = null
    private var searchItem: MenuItem? = null
    private lateinit var db: AppDatabase
    //private val welcomeScreen = WelcomeHelper(this, WelcomeActivity::class.java)

    // Utils packages
    private lateinit var su: StorageUtils
    private lateinit var smu: ServerMessagingUtils

    // Variables
    private var pressedOnce: Boolean = false
    private var selectedPage: Int = 0
    private var selectionRunning: Boolean = false
    private var showToolbar = true
    private var shownAgainOnce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start WelcomeActivity
        //welcomeScreen.show(savedInstanceState)

        // Initialize own instance
        own_instance = this

        // Initialize toolbar
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)

        // Initialize local db
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, Constants.DB_NAME).build()

        // Initialize util packages
        su = StorageUtils(this)
        smu = ServerMessagingUtils(this)

        // Initialize components
        view_pager.offscreenPageLimit = 3
        pagerAdapter = ViewPagerAdapterMain(supportFragmentManager, this, su, smu)
        view_pager.adapter = pagerAdapter
        view_pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(pos: Int) {
                if (search_view.isSearchOpen) search_view.closeSearch()

                when(pos) {
                    0 -> {
                        if(fab.isOrWillBeShown) fab.hide()
                        if(fab_network.isOrWillBeShown) fab_network.hide()
                    }
                    1 -> {
                        if(!fab.isOrWillBeShown) fab.show()
                        if(fab_network.isOrWillBeShown) fab_network.hide()
                        if (selectedPage == 2) {
                            fab.setImageResource(R.drawable.fab_anim_add_to_search)
                            val drawable = fab.drawable
                            if (drawable is Animatable) (drawable as Animatable).start()
                        }
                        selectedPage = 1
                    }
                    2 -> {
                        if(!fab.isOrWillBeShown) fab.show()
                        if(!fab_network.isOrWillBeShown) fab_network.show()
                        if (selectedPage == 1) {
                            fab.setImageResource(R.drawable.fab_anim_search_to_add)
                            val drawable = fab.drawable
                            if (drawable is Animatable) (drawable as Animatable).start()
                        }
                        selectedPage = 2
                    }
                }

                if (prevMenuItem != null) {
                    prevMenuItem?.isChecked = false
                } else {
                    bottom_navigation.menu.getItem(0).isChecked = false
                }
                bottom_navigation.menu.getItem(pos).isChecked = true
                prevMenuItem = bottom_navigation.menu.getItem(pos)
                if (searchItem != null) searchItem?.isVisible = pos != 1
            }
        })

        bottom_navigation.setOnNavigationItemSelectedListener { item ->
            view_pager.currentItem = when(item.itemId) {
                R.id.action_my_favourites -> 0
                R.id.action_all_sensors -> 1
                R.id.action_my_sensors -> 2
                else -> 0
            }
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                toolbar?.setPadding(0, insets.systemWindowInsetTop, 0, 0)
                bottom_navigation.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
                insets
            }
        }

        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        fab.setOnClickListener {
            if (view_pager.currentItem == 1) {
                val d = PlacesSearchDialog(this@MainActivity, this@MainActivity)
                val window = d.window
                window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                if (nightModeFlags == UI_MODE_NIGHT_YES) {
                    (d.search_edit_text.parent as View).setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark))
                    d.recyclerFrame.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark))
                }
                d.show()
            } else if (view_pager.currentItem == 2) {
                sheet_fab.expandFab()
            }
        }

        sheet_fab.setFabAnimationEndListener { startActivityForResult(Intent(this@MainActivity, AddSensorActivity::class.java), REQ_ADD_OWN_SENSOR) }
        sheet_fab.setFab(fab)

        fab_network.setOnClickListener {
            if(smu.isInternetAvailable && smu.isWifi) {
                sheet_fab_network.expandFab()
            } else {
                Toast.makeText(this, R.string.only_with_wifi, Toast.LENGTH_SHORT).show()
            }
        }

        sheet_fab_network.setFabAnimationEndListener { startActivityForResult(Intent(this@MainActivity, LocalNetworkActivity::class.java), REQ_SCAN_LOCAL_NETWORK) }
        sheet_fab_network.setFab(fab_network)

        fab_compare.setOnClickListener { sheet_fab_compare.expandFab() }
        sheet_fab_compare.setFabAnimationEndListener {
            // Launch CompareActivity
            val i = Intent(this@MainActivity, CompareActivity::class.java)
            i.putExtra("Sensors", pagerAdapter.selectedSensors)
            startActivityForResult(i, REQ_COMPARE)
        }
        sheet_fab_compare.setFab(fab_compare)

        fab_compare_dismiss.setOnClickListener {
            // Deselect all sensors
            pagerAdapter.deselectAllSensors()
            updateSelectionMode()
        }

        search_view.setOnQueryTextListener(object : MaterialSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                pagerAdapter.search(query, if (view_pager.currentItem == 0) SensorAdapter.MODE_FAVOURITES else SensorAdapter.MODE_OWN_SENSORS)
                updateSelectionMode()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                pagerAdapter.search(newText, if (view_pager.currentItem == 0) SensorAdapter.MODE_FAVOURITES else SensorAdapter.MODE_OWN_SENSORS)
                updateSelectionMode()
                return true
            }
        })
        if (nightModeFlags == UI_MODE_NIGHT_YES) search_view.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_light))

        // Start on the map
        view_pager.currentItem = 1

        initializeApp()
    }

    /*override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        welcomeScreen.onSaveInstanceState(outState)
    }*/

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        restartApp(this)
    }

    override fun onResume() {
        super.onResume()
        if(view_pager.currentItem > 0) {
            fab.show()
            if(view_pager.currentItem == 2) fab_network.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            WebRealtimeSyncService.own_instance?.stop()
        } catch (ignored: Exception) {}
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        searchItem = menu.findItem(R.id.action_search)
        search_view.setMenuItem(searchItem)
        searchItem?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            R.id.action_rate -> rateApp()
            R.id.action_share -> recommendApp()
            R.id.action_search -> item.expandActionView()
            R.id.action_import_export -> importExportConfiguration()
            R.id.action_help -> {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(getString(R.string.faq_url))
                startActivity(i)
            }
            R.id.action_web -> {
                val integrator = IntentIntegrator(this)
                integrator.setRequestCode(REQ_SCAN_WEB)
                integrator.setOrientationLocked(true)
                integrator.setBeepEnabled(false)
                integrator.setPrompt(getString(R.string.scan_prompt))
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                integrator.initiateScan()
            }
            R.id.action_exit -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!showToolbar) {
                toggleToolbar()
            } else {
                if (search_view.isSearchOpen) {
                    search_view.closeSearch()
                } else if (!pagerAdapter.closeInfoWindow()) {
                    if (!pressedOnce) {
                        pressedOnce = true
                        Toast.makeText(this@MainActivity, R.string.tap_again_to_exit_app, Toast.LENGTH_SHORT).show()
                        Handler().postDelayed({ pressedOnce = false }, 2500)
                    } else {
                        pressedOnce = false
                        onBackPressed()
                    }
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initializeApp() {
        // Create notification channels
        NotificationUtils.createNotificationChannels(this)

        // Request server info
        getServerInfo()

        // Start background services
        val backgroundSyncFrequency = Integer.parseInt(su.getString("sync_cycle_background", Constants.DEFAULT_SYNC_CYCLE_BACKGROUND.toString())) * 1000 * 60
        if (!isJobServiceOn(this)) {
            // Start JobScheduler
            val component = ComponentName(this, SyncJobService::class.java)
            val info = JobInfo.Builder(Constants.JOB_SYNC_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(backgroundSyncFrequency.toLong())
                .setPersisted(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) info.setRequiresBatteryNotLow(true)
            val scheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            Log.i(Constants.TAG, if (scheduler.schedule(info.build()) == JobScheduler.RESULT_SUCCESS) "Job scheduled successfully" else "Job schedule failed")
        }

        // Get data from intent
        val intent = intent
        val appLinkData = intent.data
        if (appLinkData != null && (appLinkData.toString().startsWith("https://feinstaub.chillibits.com/s/") || appLinkData.toString().startsWith("https://pm.chillibits.com/s/"))) {
            val chipId = appLinkData.toString().substring(appLinkData.toString().lastIndexOf("/") + 1)
            val random = Random()
            val color = Color.rgb(random.nextInt(255), random.nextInt(255), random.nextInt(255))

            val i = Intent(this, SensorActivity::class.java)
            i.putExtra("Name", chipId)
            i.putExtra("ID", chipId)
            i.putExtra("Color", color)
            startActivity(i)
        } else if (intent.hasExtra("ChipID")) {
            val s = su.getSensor(intent.getStringExtra("ChipID")!!)
            val i = Intent(this, SensorActivity::class.java)
            i.putExtra("Name", s!!.name)
            i.putExtra("ID", s.chipID)
            i.putExtra("Color", s.color)
            startActivity(i)
        }
    }

    fun refresh() {
        pagerAdapter.refresh()
        updateSelectionMode()
    }

    private fun rateApp() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rate))
            .setMessage(getString(R.string.rate_m))
            .setIcon(R.mipmap.ic_launcher)
            .setPositiveButton(getString(R.string.rate)) { dialog, _ ->
                dialog.dismiss()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: android.content.ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun recommendApp() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recommend))
            .setMessage(getString(R.string.recommend_m))
            .setIcon(R.mipmap.ic_launcher)
            .setPositiveButton(getString(R.string.recommend)) { dialog, _ ->
                dialog.dismiss()
                val i = Intent()
                i.action = Intent.ACTION_SEND
                i.putExtra(Intent.EXTRA_TEXT, getString(R.string.recommend_string))
                i.type = "text/plain"
                startActivity(i)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun importExportConfiguration() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_import_export, container, false)
        val d = AlertDialog.Builder(this)
            .setView(v)
            .show()

        v.import_qr.setOnClickListener {
            Handler().postDelayed({
                d.dismiss()
                val integrator = IntentIntegrator(this@MainActivity)
                integrator.run {
                    setRequestCode(REQ_SCAN_SENSOR)
                    setOrientationLocked(true)
                    setBeepEnabled(false)
                    setPrompt(getString(R.string.scan_qr_code_prompt))
                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    initiateScan()
                }
            }, 200)
        }
        v.export_qr.setOnClickListener {
            Handler().postDelayed({
                d.dismiss()

                try {
                    val sensors = pagerAdapter.selectedSensors
                    if (sensors.size > 0) {
                        var qrString = QR_PREFIX_SUFFIX
                        for (i in sensors.indices) {
                            val s = sensors[i]
                            if (i > 0) qrString = "$qrString;"
                            qrString += s.chipID
                            qrString = "$qrString,"
                            qrString += Base64.encodeToString(s.name.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
                            qrString = "$qrString,"
                            qrString += s.color.toString()
                        }
                        qrString += QR_PREFIX_SUFFIX

                        val qrView = ImageView(this@MainActivity)
                        qrView.adjustViewBounds = true
                        val multiFormatWriter = MultiFormatWriter()
                        val bitMatrix = multiFormatWriter.encode(qrString, BarcodeFormat.QR_CODE, 500, 500)
                        val barcodeEncoder = BarcodeEncoder()
                        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
                        qrView.setImageBitmap(bitmap)

                        AlertDialog.Builder(this@MainActivity)
                            .setView(qrView)
                            .setPositiveButton(getString(R.string.ok), null)
                            .setNeutralButton(getString(R.string.share_qr_code)) { _, _ -> su.shareImage(bitmap, getString(R.string.share_qr_code)) }
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.please_select_at_least_one_sensor), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_try_again), Toast.LENGTH_SHORT).show()
                }
            }, 200)
        }
        v.import_xml.setOnClickListener {
            Handler().postDelayed({
                d.dismiss()
                val properties = DialogProperties()
                properties.run {
                    selection_mode = DialogConfigs.SINGLE_MODE
                    selection_type = DialogConfigs.FILE_SELECT
                    root = File(DialogConfigs.DEFAULT_DIR)
                    error_dir = File(DialogConfigs.DEFAULT_DIR)
                    offset = File(DialogConfigs.DEFAULT_DIR)
                    extensions = arrayOf("xml")
                }
                val dialog = FilePickerDialog(this@MainActivity, properties)
                dialog.setTitle(R.string.import_xml_file)
                dialog.setDialogSelectionListener { files ->
                    su.importXMLFile(files[0])
                    refresh()
                }
                dialog.show()
            }, 200)
        }
        v.export_xml.setOnClickListener {
            Handler().postDelayed({
                d.dismiss()
                su.exportXMLFile()
            }, 200)
        }
    }

    @SuppressLint("RestrictedApi")
    fun updateSelectionMode() {
        if (pagerAdapter.selectedSensors.size >= 2) {
            if (!selectionRunning) {
                val a = AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_in)
                fab_compare.startAnimation(a)
                fab_compare_dismiss.startAnimation(a)
                fab_compare.visibility = View.VISIBLE
                fab_compare_dismiss.visibility = View.VISIBLE
                selectionRunning = true
            }
        } else {
            if (selectionRunning) {
                var a = AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_out)
                a.setAnimationListener(object : SimpleAnimationListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        fab_compare.visibility = View.GONE
                    }
                })
                fab_compare.startAnimation(a)

                a = AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_out)
                a.setAnimationListener(object : SimpleAnimationListener() {
                    override fun onAnimationEnd(animation: Animation) {
                        fab_compare_dismiss.visibility = View.GONE
                    }
                })
                fab_compare_dismiss.startAnimation(a)
                selectionRunning = false
            }
        }
    }

    fun showFab(show: Boolean) {
        if(show && view_pager.currentItem > 0) {
            fab.show()
            if(view_pager.currentItem == 2) fab_network.show()
        } else {
            fab.hide()
            fab_network.hide()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADD_OWN_SENSOR) {
            sheet_fab.contractFab()
        } else if (requestCode == REQ_COMPARE) {
            sheet_fab_compare.contractFab()
        } else if (requestCode == REQ_SCAN_LOCAL_NETWORK) {
            sheet_fab_network.contractFab()
        } else if (requestCode == MaterialSearchView.REQUEST_VOICE && resultCode == Activity.RESULT_OK) {
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (matches != null && matches.size > 0) {
                val searchWrd = matches[0]
                if (!TextUtils.isEmpty(searchWrd)) search_view.setQuery(searchWrd, false)
            }
        } else if (requestCode == REQ_SCAN_WEB && resultCode == Activity.RESULT_OK) {
            try {
                val result = IntentIntegrator.parseActivityResult(resultCode, data)
                val syncKey = result.contents
                if (syncKey.length == 25 && !syncKey.startsWith("http")) {
                    val i = Intent(this@MainActivity, WebRealtimeSyncService::class.java)
                    i.putExtra("sync_key", syncKey)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(i)
                    } else {
                        startService(i)
                    }
                    // Show toast
                    val t = Toast(this@MainActivity)
                    t.run {
                        setGravity(Gravity.CENTER, 0, 0)
                        duration = Toast.LENGTH_LONG
                        view = layoutInflater.inflate(R.layout.sync_success, null)
                        show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, R.string.error_try_again, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.error_try_again, Toast.LENGTH_SHORT).show()
            }

        } else if (requestCode == REQ_SCAN_SENSOR && resultCode == Activity.RESULT_OK) {
            try {
                val result = IntentIntegrator.parseActivityResult(resultCode, data)
                var configurationString = result.contents
                if (configurationString.startsWith(QR_PREFIX_SUFFIX) && configurationString.endsWith(QR_PREFIX_SUFFIX)) {
                    configurationString = configurationString.substring(0, configurationString.length - QR_PREFIX_SUFFIX.length).substring(QR_PREFIX_SUFFIX.length)
                    val configs = configurationString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (config in configs) {
                        val chipId = config.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                        val name = String(Base64.decode(config.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1], Base64.DEFAULT), StandardCharsets.UTF_8)
                        val color = Integer.parseInt(config.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2])
                        if (!su.isSensorExisting(chipId)) {
                            su.addFavourite(Sensor(chipId, name, color), false)
                            Toast.makeText(this@MainActivity, getString(R.string.favourite_added), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.sensor_existing), Toast.LENGTH_SHORT).show()
                        }
                    }
                    refresh()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, R.string.error_try_again, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getServerInfo() {
        if(smu.checkConnection(container)) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = loadServerInfo(this@MainActivity)
                if(result != null) {
                    su.run {
                        putInt("ServerStatus", result.serverStatus)
                        putInt("MinAppVersion", result.minAppVersion)
                        putInt("LatestAppVersion", result.latestAppVersion)
                        putString("UserMessage", result.userMessage)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        handleServerInfo(this@MainActivity, container, result)
                    }
                }
            }
        }
    }

    fun toggleToolbar() {
        if (showToolbar) {
            hideSystemBars()
        } else {
            showSystemBars()
        }
        FullscreenMode.setFullscreenMode(window, showToolbar)
        showToolbar = !showToolbar
    }

    private fun hideSystemBars() {
        val statusBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 0 else Tools.getStatusBarHeight(this)
        val navigationBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 0 else (if(shownAgainOnce) Tools.getNavigationBarHeight(this) else Tools.getNavigationBarHeight(this) * 2)
        toolbar!!.animate().translationY((-toolbar!!.measuredHeight).toFloat()).setDuration(500L).start()
        view_pager.animate().translationY((-toolbar!!.measuredHeight).toFloat()).setDuration(500L).start()
        val va = ValueAnimator.ofInt(container.measuredHeight, container.measuredHeight + bottom_navigation.measuredHeight + toolbar!!.measuredHeight + (if (shownAgainOnce) 0 else statusBarHeight) + navigationBarHeight)
        va.duration = 500L
        val layoutParams = container.layoutParams as FrameLayout.LayoutParams
        va.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            container.layoutParams = layoutParams
        }
        va.start()
        fab.hide()
    }

    private fun showSystemBars() {
        val navigationBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 0 else (if(shownAgainOnce) Tools.getNavigationBarHeight(this) else 0)
        toolbar?.animate()?.translationY(0f)?.setDuration(250L)?.start()
        toolbar?.setPadding(0, Tools.getStatusBarHeight(this), 0, 0)
        view_pager.animate().translationY(0f).setDuration(250L).start()
        val va = ValueAnimator.ofInt(container.measuredHeight, container.measuredHeight - bottom_navigation.measuredHeight - toolbar!!.measuredHeight - navigationBarHeight)
        va.duration = 250L
        val layoutParams = container.layoutParams as FrameLayout.LayoutParams
        va.addUpdateListener { animation ->
            layoutParams.height = animation.animatedValue as Int
            container.layoutParams = layoutParams
        }
        va.start()
        fab.show()
        shownAgainOnce = true
    }

    override fun onPlaceSelected(place: Place) {
        ViewPagerAdapterMain.AllSensorsFragment.moveCamera(place.latLng!!)
    }

    companion object {
        // Constants
        private const val QR_PREFIX_SUFFIX = "01010"
        const val REQ_ADD_OWN_SENSOR = 10002
        private const val REQ_COMPARE = 10003
        private const val REQ_SCAN_WEB = 10004
        private const val REQ_SCAN_SENSOR = 10005
        private const val REQ_SCAN_LOCAL_NETWORK = 10006

        // Variables as objects
        var own_instance: MainActivity? = null

        fun isJobServiceOn(context: Context): Boolean {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            return scheduler.allPendingJobs.find { it.id == Constants.JOB_SYNC_ID } != null
        }
    }
}