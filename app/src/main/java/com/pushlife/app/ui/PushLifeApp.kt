package com.pushlife.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pushlife.app.R

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Rounded.Home),
    Train(R.string.tab_train, Icons.Rounded.FitnessCenter),
    Block(R.string.tab_block, Icons.Rounded.Lock),
    Progress(R.string.tab_progress, Icons.Rounded.Insights),
    Profile(R.string.tab_profile, Icons.Rounded.Person),
}

@Composable
fun PushLifeApp(trainRequests: Int = 0) {
    var selected by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    LaunchedEffect(trainRequests) {
        if (trainRequests > 0) selected = Tab.Train.ordinal
    }
    val tab = Tab.entries[selected]

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { selected = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(onStartTraining = { selected = Tab.Train.ordinal }, modifier = modifier)
            Tab.Train -> TrainScreen(onBackHome = { selected = Tab.Home.ordinal }, modifier = modifier)
            Tab.Block -> BlockScreen(modifier)
            Tab.Progress -> ProgressScreen(modifier)
            Tab.Profile -> ProfileScreen(modifier)
        }
    }
}
