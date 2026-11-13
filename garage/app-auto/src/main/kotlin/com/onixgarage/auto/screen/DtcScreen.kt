package com.onixgarage.auto.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onixgarage.auto.di.AutoServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DtcScreen(carContext: CarContext) : Screen(carContext) {

    private val pidReader = AutoServiceLocator.pidReader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var codes: List<String> = emptyList()
    private var isLoading = false

    init {
        scope.launch { pidReader.dtcCodes.collect { codes = it; invalidate() } }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = scope.cancel()
        })
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder().apply {
            if (codes.isEmpty()) {
                setNoItemsMessage("No fault codes found")
            } else {
                codes.forEach { code ->
                    addItem(
                        Row.Builder()
                            .setTitle(code)
                            .addText(dtcCategory(code))
                            .build(),
                    )
                }
            }
        }.build()

        val refreshAction = Action.Builder()
            .setTitle("Refresh")
            .setOnClickListener {
                isLoading = true
                invalidate()
                scope.launch {
                    pidReader.refreshDtcCodes()
                    isLoading = false
                    invalidate()
                }
            }
            .build()

        return ListTemplate.Builder()
            .setTitle("Fault Codes")
            .setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(refreshAction).build())
            .setSingleList(items)
            .setLoading(isLoading)
            .build()
    }

    private fun dtcCategory(code: String): String = when (code.firstOrNull()?.uppercaseChar()) {
        'P' -> "Powertrain"
        'C' -> "Chassis"
        'B' -> "Body"
        'U' -> "Network"
        else -> "Unknown"
    }
}
