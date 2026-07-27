package com.rootapp.ui.reflection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.rootapp.ai.ChatMessage
import com.rootapp.ai.LlmClient
import com.rootapp.data.LocalStore
import com.rootapp.di.AppModule
import com.rootapp.ui.theme.LocalRootPalette

/** Builds a ReflectionViewModel with our injected LlmClient + cross-session memory. */
class ReflectionVMFactory(
    private val llm: LlmClient,
    private val userName: String,
    private val pastMemory: String,
    private val onUserMessage: (String) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReflectionViewModel(llm, userName, pastMemory, onUserMessage) as T
}

@Composable
fun ReflectionScreen(
    userName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val pastMemory = remember { store.recentMemory().joinToString("; ") }
    val vm: ReflectionViewModel = viewModel(
        factory = ReflectionVMFactory(
            llm = AppModule.llmClient,
            userName = userName,
            pastMemory = pastMemory,
            onUserMessage = { store.remember(it) },
        ),
    )
    val palette = LocalRootPalette.current
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the latest message in view.
    LaunchedEffect(state.visible.size, state.sending) {
        val count = state.visible.size + if (state.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.visible) { msg -> Bubble(msg) }
            if (state.sending) {
                item {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        state.error?.let { err ->
            Text(
                text = err,
                color = palette.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say whatever's on your mind…") },
                enabled = !state.sending,
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !state.sending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val palette = LocalRootPalette.current
    val fromUser = msg.role == "user"
    Box(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .align(if (fromUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fromUser) palette.accent else palette.surface,
            ),
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp,
                fontWeight = if (fromUser) FontWeight.Medium else FontWeight.Normal,
                color = if (fromUser) androidx.compose.ui.graphics.Color.White else palette.onSurface,
            )
        }
    }
}
