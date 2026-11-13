package com.onixgarage.phone.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.onixgarage.phone.ui.dashboard.DashboardScreen
import com.onixgarage.phone.ui.dashboard.DashboardViewModel
import com.onixgarage.phone.ui.dtc.DtcScreen
import com.onixgarage.phone.ui.dtc.DtcViewModel
import com.onixgarage.phone.ui.fuel.FuelScreen
import com.onixgarage.phone.ui.fuel.FuelViewModel
import com.onixgarage.phone.ui.service.ServiceScreen
import com.onixgarage.phone.ui.service.ServiceViewModel
import com.onixgarage.phone.ui.tires.TireScreen
import com.onixgarage.phone.ui.tires.TireViewModel
import com.onixgarage.phone.ui.trip.TripScreen
import com.onixgarage.phone.ui.trip.TripViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val FUEL = "fuel"
    const val SERVICE = "service"
    const val TIRES = "tires"
    const val TRIPS = "trips"
    const val DTC = "dtc"
}

@Composable
fun OnixNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues = PaddingValues(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
            val state by vm.uiState.collectAsState()
            DashboardScreen(state = state, onRetry = {})
        }
        composable(Routes.FUEL) {
            val vm: FuelViewModel = viewModel(factory = FuelViewModel.Factory)
            val state by vm.uiState.collectAsState()
            val formState by vm.formState.collectAsState()
            FuelScreen(
                state = state,
                formState = formState,
                onFormChange = vm::onFormChange,
                onSave = vm::onSaveFromForm,
                onDelete = vm::onDelete,
            )
        }
        composable(Routes.SERVICE) {
            val vm: ServiceViewModel = viewModel(factory = ServiceViewModel.Factory)
            val state by vm.uiState.collectAsState()
            val formState by vm.formState.collectAsState()
            ServiceScreen(
                state = state,
                formState = formState,
                onFormChange = vm::onFormChange,
                onSave = vm::onSaveFromForm,
                onDelete = vm::onDelete,
            )
        }
        composable(Routes.TIRES) {
            val vm: TireViewModel = viewModel(factory = TireViewModel.Factory)
            val state by vm.uiState.collectAsState()
            val formState by vm.formState.collectAsState()
            TireScreen(
                state = state,
                formState = formState,
                onFormChange = vm::onFormChange,
                onSave = vm::onSaveFromForm,
                onDelete = vm::onDelete,
            )
        }
        composable(Routes.TRIPS) {
            val vm: TripViewModel = viewModel(factory = TripViewModel.Factory)
            val state by vm.uiState.collectAsState()
            TripScreen(
                state = state,
                onPeriodChange = vm::onPeriodChanged,
            )
        }
        composable(Routes.DTC) {
            val vm: DtcViewModel = viewModel(factory = DtcViewModel.Factory)
            val state by vm.uiState.collectAsState()
            DtcScreen(
                state = state,
                onRefresh = vm::onRefresh,
                onClear = vm::onClearDtcs,
            )
        }
    }
}
