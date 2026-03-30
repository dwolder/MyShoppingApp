package com.myshoppinglist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myshoppinglist.data.local.entity.GroceryCategory
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.ui.components.QuickAddBar
import com.myshoppinglist.ui.viewmodel.CategoryGroup
import com.myshoppinglist.ui.viewmodel.ShoppingItemViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStoreSearch: (String) -> Unit,
    viewModel: ShoppingItemViewModel = hiltViewModel()
) {
    val listName by viewModel.listName.collectAsStateWithLifecycle()
    val groupedItems by viewModel.groupedItems.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddDialog.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val checkedCount by viewModel.checkedCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            listName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalCount > 0) {
                            Text(
                                "$checkedCount of $totalCount items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToStoreSearch(viewModel.listId) }) {
                        Icon(Icons.Default.Search, "Search stores")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Uncheck all") },
                                onClick = {
                                    viewModel.uncheckAll()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete checked items") },
                                onClick = {
                                    viewModel.deleteCheckedItems()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            QuickAddBar(
                onAddItem = { name ->
                    viewModel.addItem(name, 1.0, "", GroceryCategory.OTHER.displayName, "")
                }
            )

            if (totalCount > 0) {
                val progress by animateFloatAsState(
                    targetValue = checkedCount.toFloat() / totalCount.toFloat(),
                    label = "progress"
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (groupedItems.isEmpty()) {
                EmptyItemsState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedItems.forEach { group ->
                        item(key = "header_${group.category.name}") {
                            CategoryHeader(group)
                        }
                        items(group.items, key = { it.id }) { item ->
                            SwipeableItemCard(
                                item = item,
                                onToggleChecked = { viewModel.toggleItemChecked(item) },
                                onEdit = { viewModel.startEditing(item) },
                                onDelete = { viewModel.deleteItem(item.id) }
                            )
                        }
                        item(key = "spacer_${group.category.name}") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onAdd = { name, qty, unit, category, notes ->
                viewModel.addItem(name, qty, unit, category, notes)
            }
        )
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { viewModel.stopEditing() },
            onSave = { updated -> viewModel.updateItem(updated) }
        )
    }
}

@Composable
private fun EmptyItemsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "List is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap + to add items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CategoryHeader(group: CategoryGroup) {
    val allChecked = group.items.all { it.isChecked }
    val headerAlpha by animateFloatAsState(
        targetValue = if (allChecked) 0.5f else 1f,
        label = "headerAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(headerAlpha)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.category.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "(${group.items.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItemCard(
    item: ShoppingItemEntity,
    onToggleChecked: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        ShoppingItemCard(
            item = item,
            onToggleChecked = onToggleChecked,
            onEdit = onEdit
        )
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItemEntity,
    onToggleChecked: () -> Unit,
    onEdit: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (item.isChecked) 0.5f else 1f,
        label = "itemAlpha"
    )

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleChecked,
                modifier = Modifier.size(32.dp)
            ) {
                AnimatedVisibility(
                    visible = item.isChecked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Checked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                AnimatedVisibility(
                    visible = !item.isChecked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        Icons.Outlined.Circle,
                        contentDescription = "Unchecked",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildString {
                    if (item.quantity != 1.0 || item.unit.isNotBlank()) {
                        append("${formatQuantity(item.quantity)} ${item.unit}".trim())
                    }
                    if (item.notes.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.notes)
                    }
                }
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatQuantity(qty: Double): String {
    return if (qty == qty.toLong().toDouble()) {
        qty.toLong().toString()
    } else {
        qty.toString()
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableDoubleStateOf(1.0) }
    var unit by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(GroceryCategory.OTHER.displayName) }
    var notes by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    placeholder = { Text("e.g., Milk") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (quantity == 1.0) "" else formatQuantity(quantity),
                        onValueChange = { quantity = it.toDoubleOrNull() ?: 1.0 },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("e.g., kg, L") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = showCategoryPicker,
                        onDismissRequest = { showCategoryPicker = false }
                    ) {
                        GroceryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = {
                                    category = cat.displayName
                                    showCategoryPicker = false
                                }
                            )
                        }
                    }
                    // Invisible overlay to trigger the dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .padding(0.dp)
                    ) {
                        TextButton(
                            onClick = { showCategoryPicker = true },
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g., organic, 2% fat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onAdd(name, quantity, unit, category, notes) }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, quantity, unit, category, notes) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditItemDialog(
    item: ShoppingItemEntity,
    onDismiss: () -> Unit,
    onSave: (ShoppingItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableDoubleStateOf(item.quantity) }
    var unit by remember { mutableStateOf(item.unit) }
    var category by remember { mutableStateOf(item.category) }
    var notes by remember { mutableStateOf(item.notes) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = formatQuantity(quantity),
                        onValueChange = { quantity = it.toDoubleOrNull() ?: 1.0 },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = showCategoryPicker,
                        onDismissRequest = { showCategoryPicker = false }
                    ) {
                        GroceryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = {
                                    category = cat.displayName
                                    showCategoryPicker = false
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                    ) {
                        TextButton(
                            onClick = { showCategoryPicker = true },
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) {
                                onSave(item.copy(
                                    name = name.trim(),
                                    quantity = quantity,
                                    unit = unit,
                                    category = category,
                                    notes = notes.trim()
                                ))
                            }
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(item.copy(
                        name = name.trim(),
                        quantity = quantity,
                        unit = unit,
                        category = category,
                        notes = notes.trim()
                    ))
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
