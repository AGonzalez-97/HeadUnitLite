package com.onixgarage.headunit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onixgarage.headunit.ui.HuDashboardScreen
import com.onixgarage.headunit.ui.HuViewModel
import com.onixgarage.headunit.ui.theme.OnixGarageHuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnixGarageHuTheme {
                val vm: HuViewModel = viewModel(factory = HuViewModel.Factory)
                val state by vm.uiState.collectAsState()
                HuDashboardScreen(state = state)
            }
        }
    }
}
