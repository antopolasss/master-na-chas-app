package com.master.hour

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.room.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val address: String = "",
    val amount: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE type = :type ORDER BY createdAt DESC")
    fun observe(type: String): kotlinx.coroutines.flow.Flow<List<Entry>>
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<Entry>>
    @Insert suspend fun insert(entry: Entry)
    @Delete suspend fun delete(entry: Entry)
    @Query("UPDATE entries SET type = 'income', createdAt = :now WHERE id = :id")
    suspend fun completeRequest(id: Long, now: Long = System.currentTimeMillis())
}

@Database(entities = [Entry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): EntryDao
    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: android.content.Context): AppDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "master_na_chas.db").build().also { INSTANCE = it }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MasterApp(AppDb.get(this).dao()) } }
    }
}

private data class HomeAction(val title: String, val icon: ImageVector, val key: String)

@Composable
fun MasterApp(dao: EntryDao) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { importHistoricalData(context, dao) }
    var page by remember { mutableStateOf("home") }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val requests by dao.observe("request").collectAsState(initial = emptyList())
    val incomes by dao.observe("income").collectAsState(initial = emptyList())
    val expenses by dao.observe("expense").collectAsState(initial = emptyList())
    val all by dao.observeAll().collectAsState(initial = emptyList())
    val actions = listOf(
        HomeAction("Доход", Icons.Default.AccountBalanceWallet, "income"),
        HomeAction("Расход", Icons.Default.ShoppingCart, "expense"),
        HomeAction("Заявки", Icons.Default.CalendarMonth, "request"),
        HomeAction("Аналитика", Icons.Default.BarChart, "info"),
        HomeAction("Средняя цена", Icons.Default.Search, "prices")
    )
    val titles = mapOf("income" to "Доход", "expense" to "Расход", "request" to "Заявки", "info" to "Аналитика", "prices" to "Средняя цена")
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            if (page == "home") {
                Text("МАСТЕР НА ЧАС", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Рабочий кабинет", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Доход за всё время", style = MaterialTheme.typography.labelLarge)
                        Text("${incomes.sumOf { it.amount }} ₸", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("${incomes.size} записей в истории", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(18.dp))
                actions.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        pair.forEach { action ->
                            Card(Modifier.weight(1f).height(112.dp).clickable { page = action.key }, shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(action.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(12.dp))
                                    Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Text("Активных заявок: ${requests.size}", style = MaterialTheme.typography.titleMedium)
                Text("Доход за всё время: ${incomes.sumOf { it.amount }} ₸")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "home" }) { Icon(Icons.Default.ArrowBack, "Назад") }
                    Text(titles[page] ?: "", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    if (page != "info") IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Добавить") }
                }
                Spacer(Modifier.height(12.dp))
                when (page) {
                    "request" -> EntryList(requests, true, onDone = { item -> scope.launch { dao.completeRequest(item.id) } }, onDelete = { item -> scope.launch { dao.delete(item) } })
                    "income" -> EntryList(incomes, false, onDelete = { item -> scope.launch { dao.delete(item) } })
                    "expense" -> EntryList(expenses, false, onDelete = { item -> scope.launch { dao.delete(item) } })
                    "info" -> InfoScreen(incomes, expenses, requests, all)
                    "prices" -> PriceSearchScreen(incomes)
                }
            }
        }
    }
    if (showAdd) {
        val type = page
        AddEntryDialog(type, onDismiss = { showAdd = false }, onSave = { title, address, amount, note ->
            scope.launch { dao.insert(Entry(type = type, title = title, address = address, amount = amount, note = note)) }
            showAdd = false
        })
    }
}

@Composable
fun EntryList(entries: List<Entry>, isRequest: Boolean, onDone: (Entry) -> Unit = {}, onDelete: (Entry) -> Unit) {
    if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пока записей нет") }
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries, key = { it.id }) { item ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.address.isNotBlank()) Text(item.address)
                    Text("${item.amount} ₸", style = MaterialTheme.typography.titleLarge)
                    if (item.note.isNotBlank()) Text(item.note)
                    Text(SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isRequest) TextButton(onClick = { onDone(item) }) { Text("✓ Выполнено, в доход") }
                        TextButton(onClick = { onDelete(item) }) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoScreen(incomes: List<Entry>, expenses: List<Entry>, requests: List<Entry>, all: List<Entry>) {
    val income = incomes.sumOf { it.amount }
    val expense = expenses.sumOf { it.amount }
    val week = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val weekIncome = incomes.filter { it.createdAt >= week }.sumOf { it.amount }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatCard("Доход за всё время", income) }
        item { StatCard("Расходы за всё время", expense) }
        item { StatCard("Чистый результат", income - expense) }
        item { StatCard("Доход за 7 дней", weekIncome) }
        item { Text("Активных заявок: ${requests.size}", style = MaterialTheme.typography.titleMedium) }
        item { Text("Всего операций: ${all.size}") }
    }
}

@Composable
fun StatCard(title: String, amount: Long) {
    Card { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(title); Text("${amount} ₸", style = MaterialTheme.typography.headlineSmall) } }
}

@Composable
fun AddEntryDialog(type: String, onDismiss: () -> Unit, onSave: (String, String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val label = when(type) { "request" -> "Новая заявка"; "income" -> "Новый доход"; else -> "Новый расход" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(if (type == "expense") "Что купил" else "Что сделать / за что получил") }, singleLine = true)
                if (type != "expense") OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() } }, label = { Text(if (type == "request") "Договорённая сумма, ₸" else "Сумма, ₸") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0, onClick = { onSave(title.trim(), address.trim(), amount.toLong(), note.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun priceKey(value: String): String = value.lowercase(Locale("ru")).replace('ё', 'е').filter { it.isLetterOrDigit() }

private fun priceMatch(query: String, title: String): Boolean {
    val q = priceKey(query)
    val t = priceKey(title)
    if (q.length < 3) return false
    if (t.contains(q) || q.contains(t)) return true
    val stem = q.take( maxOf(3, q.length - 2) )
    return t.contains(stem)
}

@Composable
fun PriceSearchScreen(incomes: List<Entry>) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, incomes) {
        incomes.filter { item ->
            val name = priceKey(item.title)
            !name.contains("кобулетти") && !name.contains("кабулетти") && priceMatch(query, item.title)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Найди работу по названию", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Например, комод или шкаф") }, singleLine = true)
        if (query.isNotBlank() && matches.isNotEmpty()) {
            val average = matches.sumOf { it.amount } / matches.size
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Средняя цена", style = MaterialTheme.typography.labelLarge)
                    Text("$average ₸", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("От ${matches.minOf { it.amount }} до ${matches.maxOf { it.amount }} ₸ · ${matches.size} совпадений")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches.sortedByDescending { it.createdAt }, key = { it.id }) { item ->
                    Card { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Medium); Text(SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall) }
                        Text("${item.amount} ₸", fontWeight = FontWeight.SemiBold)
                    } }
                }
            }
        } else if (query.isNotBlank()) {
            Text("Похожих работ пока не нашёл. Попробуй короче или иначе написать название.")
        } else {
            Text("Введи название работы. Я найду похожие записи в истории и посчитаю среднюю стоимость.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}