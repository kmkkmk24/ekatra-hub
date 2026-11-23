package com.kaushikkm.ekatrahub.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaushikkm.ekatrahub.R

/**
 * "Prominent disclosure" - Google Play requires this BEFORE sending the user to grant a
 * sensitive permission: say plainly what is collected, why, and where it goes,
 * and only continue after the user taps an explicit button.
 */
@Composable
fun DisclosureScreen(onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .padding(top = 24.dp)
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF2B1B3D)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
        }
        Text(
            "Every message. One place.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Ekatra gathers the notifications from all your apps into a single timeline, so you never lose one again.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        InfoCard(
            "What Ekatra reads",
            "The title and text of notifications from your apps (messages, email, alerts), " +
                "the app that sent them and when.",
        )
        InfoCard(
            "Where it goes",
            "Nowhere. Everything is stored only on this phone. Ekatra has no internet " +
                "permission, no account and no analytics, so it cannot upload anything.",
        )
        InfoCard(
            "You stay in control",
            "Delete one item, one app's history or everything at any time. Turn access off in " +
                "Settings whenever you like. Uninstalling erases all data.",
        )

        Button(
            onClick = onAllow,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Allow notification access")
        }
        Text(
            "On the next screen, switch on Ekatra Hub, then come back here.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
