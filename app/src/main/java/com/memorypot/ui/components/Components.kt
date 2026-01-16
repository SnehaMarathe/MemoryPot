package com.memorypot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memorypot.data.repo.Confidence
import com.memorypot.data.repo.LocationSuggestion
import android.os.Build
import androidx.compose.foundation.layout.RowScope

/**
 * A “Play Store ready” top bar:
 * - centered title
 * - optional back button
 * - optional single action icon button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    large: Boolean = false
) {
    val colors = if (large) {
        TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    } else {
        TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    }

    if (large) {
        androidx.compose.material3.LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = {
                if (actionIcon != null && onAction != null) {
                    IconButton(onClick = onAction) {
                        Icon(actionIcon, contentDescription = actionLabel ?: "")
                    }
                }
            },
            colors = colors
        )
        return
    }

    CenterAlignedTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (actionIcon != null && onAction != null) {
                IconButton(onClick = onAction) {
                    Icon(actionIcon, contentDescription = actionLabel ?: "")
                }
            }
        },
        colors = colors
    )
}

/**
 * Mass-market friendly search: large touch targets, clear iconography,
 * and a one-tap clear button.
 */
@Composable
fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search your memories"
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        TextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValue("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

/**
 * iOS-like inline search pill (Apple Photos-ish): soft gray surface, large touch target.
 */
@Composable
fun IOSSearchField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        TextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValue("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun ConfidenceChip(confidence: Confidence, modifier: Modifier = Modifier) {
    val label = when (confidence) {
        Confidence.LOW -> "Low"
        Confidence.MEDIUM -> "Medium"
        Confidence.HIGH -> "High"
    }
    AssistChip(
        modifier = modifier,
        onClick = {},
        enabled = false,
        label = { Text("Confidence: $label") },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Suggestions are intentionally “assistant-like”: calm card, strong title,
 * top 3 locations, and an optional action.
 */
@Composable
fun SuggestionCard(
    query: String,
    suggestions: List<LocationSuggestion>,
    onTapPlace: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (query.isBlank() || suggestions.isEmpty()) return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Possible locations for “${query.trim()}”",
                style = MaterialTheme.typography.titleMedium
            )
            suggestions.take(3).forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onTapPlace != null) { onTapPlace?.invoke(s.placeText) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.placeText, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.count}× • last seen ${android.text.format.DateUtils.getRelativeTimeSpanString(s.lastSeenAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    ConfidenceChip(s.confidence)
                }
            }

            if (onTapPlace != null) {
                OutlinedButton(onClick = { onTapPlace(suggestions.first().placeText) }) {
                    Text("Use top suggestion")
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// -------------------------
// iOS-like grouped sections
// -------------------------

@Composable
fun IOSSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

/**
 * iOS-inspired "grouped inset" surface.
 * Use [IOSRow] inside for nice separators.
 */
@Composable
fun IOSGroupedSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
fun IOSRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
    if (showDivider) {
        Divider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * Sticky bottom actions (iOS-like): translucent bar that stays reachable
 * with keyboard + nav bars.
 */
@Composable
fun IOSBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // iOS-like “frosted” bar: we use a translucent surface for broad compatibility.
    // (A true blur can be added later with proper compose RenderEffect bridging.)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
fun InlineRowKeyValue(key: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Pill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -------------------------
// Premium keyword chips UI
// -------------------------

private fun parseKeywords(raw: String): List<String> =
    raw
        .split(',', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

private fun toKeywordString(keywords: List<String>): String = keywords.joinToString(", ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChipsDisplay(
    keywords: String,
    modifier: Modifier = Modifier,
    title: String = "Keywords"
) {
    val list = parseKeywords(keywords)
    if (list.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            list.forEach { kw ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(kw) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * A premium editor:
 * - shows keywords as removable chips
 * - merges user prompt keywords into the existing list
 * - keeps persistence format as a single comma-separated string
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordEditor(
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onApplyPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Keywords",
    supportingText: String = "AI suggestions are editable. Add your own keywords and we’ll merge them."
) {
    val list = parseKeywords(keywords)

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (list.isEmpty()) {
                Text(
                    "No keywords yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    list.forEach { kw ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(kw, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val updated = list.filterNot { it.equals(kw, ignoreCase = true) }
                                    onKeywordsChange(toKeywordString(updated))
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Add more keywords") },
                    placeholder = { Text("e.g., passport, hotel, keys") }
                )
                OutlinedButton(onClick = onApplyPrompt, enabled = prompt.trim().isNotBlank()) {
                    Text("Add")
                }
            }

            // Advanced (paste/edit) — keeps power users happy
            OutlinedTextField(
                value = keywords,
                onValueChange = onKeywordsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Edit as text") },
                placeholder = { Text("Comma-separated keywords") },
                minLines = 2
            )
        }
    }
}
