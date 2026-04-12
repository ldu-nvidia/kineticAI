package com.mycarv.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mycarv.app.ui.tabs.GearTab
import com.mycarv.app.ui.tabs.MoreTab
import com.mycarv.app.ui.tabs.RunTab
import com.mycarv.app.ui.tabs.StatsTab
import com.mycarv.app.ui.tabs.TrainTab

data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val tabs = listOf(
    TabItem("Run", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
    TabItem("Train", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    TabItem("Stats", Icons.Filled.InsertChart, Icons.Outlined.InsertChart),
    TabItem("Gear", Icons.Filled.Bluetooth, Icons.Outlined.Bluetooth),
    TabItem("More", Icons.Filled.Menu, Icons.Outlined.Menu),
)

@Composable
fun MainScaffold() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var hideBottomBar by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            if (hideBottomBar == 0) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> RunTab(
                modifier = modifier,
                onHideTabBar = { hideBottomBar = 1 },
                onShowTabBar = { hideBottomBar = 0 },
            )
            1 -> TrainTab(
                modifier = modifier,
                onHideTabBar = { hideBottomBar = 1 },
                onShowTabBar = { hideBottomBar = 0 },
            )
            2 -> StatsTab(modifier = modifier)
            3 -> GearTab(modifier = modifier)
            4 -> MoreTab(modifier = modifier)
        }
    }
}
