package com.hive.agents.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.agents.agent.ModelRouter
import com.hive.agents.data.Agent
import com.hive.agents.data.HiveState
import com.hive.agents.data.Message
import com.hive.agents.data.MessageKind
import com.hive.agents.ui.AgentAvatar
import com.hive.agents.ui.agentColor
import com.hive.agents.ui.theme.Amber
import com.hive.agents.ui.theme.Danger
import com.hive.agents.ui.theme.Good
import com.hive.agents.ui.theme.PanelHigh
import com.hive.agents.ui.theme.Teal
import com.hive.agents.ui.theme.TextDim
import com.hive.agents.ui.theme.TextMain
import com.hive.agents.ui.timeAgo

private val Night = Color(0xFF000000)
private val Rail = Color(0xFF09090B)
private val BotBubble = Color(0xFF26262A)
private val HumanBubble = Color(0xFF0A84FF)
private val Hairline = Color(0xFF1E1E20)

/**
 * The company chat. The left rail is the crew, not a list of threads: pick the whole company or
 * one bot. Bot-to-bot handoffs are real messages in both participants' threads, so the overseer
 * can peer into a conversation without taking it over.
 */
@Composable
fun ChatScreen(
    state: HiveState,
    busy: Boolean,
    onSend: (agentId: String, text: String) -> Unit,
    onOpenViz: () -> Unit,
    onOpenTeam: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { state.agent(it) }
    LaunchedEffect(state.agents) {
        if (selectedId != null && selected == null) selectedId = null
    }

    val company = selected == null
    val messages = state.messages
        .sortedBy { it.createdAt }
        .filter { message ->
            company || message.agentId == selectedId || message.toAgentId == selectedId
        }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, selectedId) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.size) }
        }
    }

    Row(Modifier.fillMaxSize().background(Night)) {
        CrewRail(
            agents = state.agents,
            company = company,
            selectedId = selectedId,
            onSelectCompany = { selectedId = null },
            onSelect = { selectedId = it },
            onOpenTeam = onOpenTeam
        )

        Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            ChatHeader(
                state = state,
                selected = selected,
                company = company,
                onOpenViz = onOpenViz,
                onOpenTeam = onOpenTeam
            )
            HorizontalDivider(color = Hairline)

            if (busy) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF0D1714)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Good))
                    Spacer(Modifier.width(6.dp))
                    Text("The company is working...", fontSize = 10.sp, color = Good)
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        if (company) "COMPANY THREAD" else "DIRECT + HANDOFFS",
                        fontSize = 9.sp,
                        color = TextDim,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }

                if (messages.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet. Chief and the crew will show their handoffs here.",
                            fontSize = 12.sp,
                            color = TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp)
                        )
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageRow(state = state, message = message, company = company)
                }
            }

            Composer(
                targetName = selected?.name ?: state.orchestrator?.name,
                enabled = state.orchestrator != null,
                onSend = { text ->
                    val target = selected ?: state.orchestrator
                    if (target != null) onSend(target.id, text)
                }
            )
        }
    }
}

@Composable
private fun CrewRail(
    agents: List<Agent>,
    company: Boolean,
    selectedId: String?,
    onSelectCompany: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenTeam: () -> Unit
) {
    Column(
        Modifier.width(76.dp).fillMaxHeight().background(Rail).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HIVE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Amber)
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (company) Color(0xFF1C1C1E) else Color.Transparent)
                .clickable(onClick = onSelectCompany)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Amber, Color(0xFFFF8A3D)))),
                contentAlignment = Alignment.Center
            ) {
                Text("H", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF161006))
            }
            Spacer(Modifier.height(3.dp))
            Text("Company", fontSize = 8.sp, color = if (company) TextMain else TextDim)
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(40.dp).height(1.dp).background(Hairline))
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(agents, key = { it.id }) { agent ->
                val selected = selectedId == agent.id
                Column(
                    Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFF1C1C1E) else Color.Transparent)
                        .clickable { onSelect(agent.id) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AgentAvatar(agent, size = 34.dp, selected = selected, showOnline = true)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        agent.name,
                        fontSize = 8.sp,
                        color = if (selected) TextMain else TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        TextButton(onClick = onOpenTeam, contentPadding = PaddingValues(0.dp)) {
            Text("+ crew", fontSize = 9.sp, color = Teal)
        }
    }
}

@Composable
private fun ChatHeader(
    state: HiveState,
    selected: Agent?,
    company: Boolean,
    onOpenViz: () -> Unit,
    onOpenTeam: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Night).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            AgentAvatar(selected, size = 34.dp, selected = true, showOnline = false)
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Amber, Color(0xFFFF8A3D)))),
                contentAlignment = Alignment.Center
            ) {
                Text("H", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF161006))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                selected?.name ?: "Company",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain
            )
            Text(
                if (company) {
                    "${state.agents.size} bots - every handoff is visible"
                } else {
                    "${selected?.role ?: "bot"} - ${selected?.let { ModelRouter.label(it, state.settings) } ?: ""}"
                },
                fontSize = 9.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onOpenViz) { Text("3D", fontSize = 11.sp, color = Amber) }
        TextButton(onClick = onOpenTeam) { Text("+", fontSize = 19.sp, color = Teal) }
    }
}

@Composable
private fun MessageRow(state: HiveState, message: Message, company: Boolean) {
    if (message.kind == MessageKind.SYSTEM || message.kind == MessageKind.NOTE) {
        val error = message.body.contains("failed", true) ||
            message.body.contains("blocked", true) ||
            message.body.contains("HTTP ", true)
        Text(
            message.body.take(500),
            fontSize = 9.sp,
            lineHeight = 13.sp,
            color = if (error) Danger else TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (error) Color(0xFF2A1114) else Color(0xFF111113))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        return
    }

    if (message.fromUser) {
        val target = state.agent(message.agentId)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 300.dp)) {
                Text(
                    if (target != null) "You -> ${target.name}" else "You",
                    fontSize = 8.sp,
                    color = TextDim,
                    modifier = Modifier.padding(end = 4.dp, bottom = 3.dp)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(HumanBubble)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    RichBody(message.body, onBlue = true)
                }
                Text(
                    timeAgo(message.createdAt),
                    fontSize = 8.sp,
                    color = TextDim,
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                )
            }
        }
        return
    }

    val agent = state.agent(message.agentId)
    val to = message.toAgentId.takeIf { it.isNotBlank() }?.let { state.agent(it) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (agent != null) {
            AgentAvatar(agent, size = 27.dp, showOnline = false)
        } else {
            Box(
                Modifier.size(27.dp).clip(RoundedCornerShape(9.dp)).background(PanelHigh),
                contentAlignment = Alignment.Center
            ) { Text("?", fontSize = 10.sp, color = TextDim) }
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.widthIn(max = 300.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    agent?.name ?: "Bot",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = agent?.let { agentColor(it) } ?: TextDim
                )
                if (to != null) {
                    Text(
                        "  ->  ${to.name}",
                        fontSize = 8.sp,
                        color = if (message.kind == MessageKind.HANDOFF) Good else Amber
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(timeAgo(message.createdAt), fontSize = 8.sp, color = TextDim)
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(BotBubble)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                RichBody(message.body, onBlue = false)
            }
            if (!company && to == null) {
                Text("in the company thread", fontSize = 8.sp, color = TextDim, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
        }
    }
}

@Composable
private fun Composer(targetName: String?, enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth().background(Night).imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
            cursorBrush = SolidColor(HumanBubble),
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isBlank()) {
                        Text(
                            "Message ${targetName ?: "the company"}...",
                            fontSize = 13.sp,
                            color = TextDim
                        )
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                }
            },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (enabled && text.isNotBlank()) HumanBubble else Color(0xFF2C2C2E))
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

private data class Chunk(val code: Boolean, val lang: String, val text: String)

private fun splitCode(raw: String): List<Chunk> {
    val out = mutableListOf<Chunk>()
    var index = 0
    while (index < raw.length) {
        val start = raw.indexOf("```", index)
        if (start < 0) {
            out += Chunk(false, "", raw.substring(index))
            break
        }
        if (start > index) out += Chunk(false, "", raw.substring(index, start))

        val headerEnd = raw.indexOf('\n', start + 3).let { if (it < 0) start + 3 else it }
        val lang = raw.substring(start + 3, headerEnd).trim()
        val end = raw.indexOf("```", headerEnd + 1)
        if (end < 0) {
            out += Chunk(false, "", raw.substring(start))
            break
        }
        out += Chunk(true, lang, raw.substring(headerEnd + 1, end).trim('\n'))
        index = end + 3
    }
    return out
}

private fun inlineCode(raw: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < raw.length) {
        val tick = raw.indexOf('`', index)
        if (tick < 0) {
            append(raw.substring(index))
            break
        }
        append(raw.substring(index, tick))
        val end = raw.indexOf('`', tick + 1)
        if (end < 0) {
            append(raw.substring(tick))
            break
        }
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Teal, background = Color(0xFF1C1C1E))) {
            append(raw.substring(tick + 1, end))
        }
        index = end + 1
    }
}

@Composable
private fun RichBody(text: String, onBlue: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        splitCode(text).forEach { chunk ->
            if (chunk.code) {
                CodeBlock(chunk.lang, chunk.text)
            } else if (chunk.text.isNotBlank()) {
                Text(
                    inlineCode(chunk.text.trim()),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = if (onBlue) Color.White else TextMain
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E0E10))
            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lang.ifBlank { "code" }.uppercase(),
                fontSize = 8.sp,
                color = TextDim,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp)
            ) { Text("Copy", fontSize = 9.sp, color = Teal) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
        ) {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFFE8E8ED)
            )
        }
    }
}
