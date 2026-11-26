package com.master.hour

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
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
    @Update suspend fun update(entry: Entry)
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
    val context=LocalContext.current
    LaunchedEffect(Unit){importHistoricalData(context,dao)}
    var page by remember{mutableStateOf("home")}
    var showAdd by remember{mutableStateOf(false)}
    var showPicker by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val requests by dao.observe("request").collectAsState(initial=emptyList())
    val incomes by dao.observe("income").collectAsState(initial=emptyList())
    val expenses by dao.observe("expense").collectAsState(initial=emptyList())
    val all by dao.observeAll().collectAsState(initial=emptyList())
    val actions=listOf(HomeAction("Доходы",Icons.Default.AccountBalanceWallet,"income"),HomeAction("Расходы",Icons.Default.ShoppingCart,"expense"),HomeAction("Заявки",Icons.Default.CalendarMonth,"request"),HomeAction("Аналитика",Icons.Default.BarChart,"info"),HomeAction("Материалы",Icons.Default.CollectionsBookmark,"materials"),HomeAction("Средняя цена",Icons.Default.Search,"prices"))
    val titles=mapOf("income" to "Доходы","expense" to "Расходы","request" to "Заявки","info" to "Аналитика","prices" to "Средняя цена","materials" to "Материалы")
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF203B63),onPrimary=Color.White,primaryContainer=Color(0xFFD8E3F1),secondary=Color(0xFF5279A8),background=Color(0xFFEAF0F7),surface=Color.White)){
        Scaffold(containerColor=MaterialTheme.colorScheme.background){padding->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp,vertical=14.dp)){
                if(page=="home"){
                    Text("МАСТЕР НА ЧАС",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Рабочий кабинет",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF203B63))){
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(if(requests.isEmpty()) "ПОЛЕЗНОЕ" else "БЛИЖАЙШИЕ ЗАЯВКИ",color=Color.White.copy(alpha=.78f),fontWeight=FontWeight.Bold)
                            if(requests.isEmpty()){
                                Text("Пока активных заказов нет",color=Color.White,style=MaterialTheme.typography.titleMedium)
                                Text("Добавляй новые заявки, чтобы держать планы под рукой",color=Color.White.copy(alpha=.82f),style=MaterialTheme.typography.bodySmall)
                            } else {
                                requests.take(3).forEach { req ->
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){
                                            Text(req.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1)
                                            if(req.address.isNotBlank()) Text(req.address,color=Color.White.copy(alpha=.75f),style=MaterialTheme.typography.bodySmall,maxLines=1)
                                        }
                                        if(req.amount>0) Text("${req.amount} ₸",color=Color.White,fontWeight=FontWeight.Bold)
                                    }
                                }
                                TextButton(onClick={page="request"}){Text("Все заявки",color=Color(0xFFD8E3F1))}
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("РАБОЧИЕ ИНСТРУМЕНТЫ",color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    actions.chunked(2).forEach{pair->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(bottom=10.dp)){
                            pair.forEach{action->
                                Card(Modifier.weight(1f).height(88.dp).clickable{page=action.key},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                                    Row(Modifier.fillMaxSize().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                        Icon(action.icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                        Text(action.title,fontWeight=FontWeight.SemiBold)
                                    }
                                }
                            }
                            if(pair.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("Активных заявок: "+requests.size,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }else{
                    Row(verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={page="home"}){Icon(Icons.Default.ArrowBack,"Назад")}
                        Text(titles[page] ?: "",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    when(page){
                        "income"->DayLedger(incomes,true,{showAdd=true},{showPicker=true},{item->scope.launch{dao.delete(item)}})
                        "expense"->ExpenseScreen(expenses,{showAdd=true},{item->scope.launch{dao.delete(item)}},{item,title,amount->scope.launch{dao.update(item.copy(title=title,amount=amount))}})
                        "request"->RequestScreen(requests,{title,address,amount,note->scope.launch{dao.insert(Entry(type="request",title=title,address=address,amount=amount,note=note))}},{item->scope.launch{dao.completeRequest(item.id)}},{item->scope.launch{dao.delete(item)}})
                        "info"->InfoScreen(incomes,expenses,requests,all)
                        "prices"->PriceSearchScreen(incomes)
                        "materials"->MaterialScreen(all.filter{it.type=="material"},{title,uri,mime->scope.launch{dao.insert(Entry(type="material",title=title,address=uri,note=mime,amount=0))}},{item->scope.launch{dao.delete(item)}})
                    }
                }
            }
        }
        if(showAdd){
            val type=page
            AddEntryDialog(type,{showAdd=false},{title,address,amount,note->
                scope.launch{dao.insert(Entry(type=type,title=title,address=address,amount=amount,note=note))}
                showAdd=false
            })
        }
        if(showPicker)AlertDialog(onDismissRequest={showPicker=false},title={Text("Активные заявки")},text={
            if(requests.isEmpty())Text("Активных заявок пока нет")
            else LazyColumn{items(requests,key={it.id}){item->
                Row(Modifier.fillMaxWidth().clickable{scope.launch{dao.completeRequest(item.id);showPicker=false}}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(item.title,fontWeight=FontWeight.SemiBold);if(item.address.isNotBlank())Text(item.address,style=MaterialTheme.typography.bodySmall)}
                    Text(item.amount.toString()+" ₸",fontWeight=FontWeight.Bold)
                    Icon(Icons.Default.ChevronRight,null)
                }
                HorizontalDivider()
            }}
        },confirmButton={TextButton(onClick={showPicker=false}){Text("Закрыть")}})
    }
}

@Composable
fun DayLedger(entries:List<Entry>,isIncome:Boolean,onAdd:()->Unit,onPick:()->Unit,onDelete:(Entry)->Unit){
    val days=(0..20).map{offset->java.util.Calendar.getInstance().apply{
        add(java.util.Calendar.DAY_OF_YEAR,-offset)
        set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
    }.timeInMillis}
    Column(Modifier.fillMaxSize()){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick=onAdd,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Add,null);Text("Новая запись")}
            if(isIncome)OutlinedButton(onClick=onPick,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Assignment,null);Text("Список заявок")}
        }
        Spacer(Modifier.height(13.dp))
        Text("ИСТОРИЯ · ПО ДНЯМ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){
            items(days,key={it}){day->
                val cal=java.util.Calendar.getInstance().apply{timeInMillis=day}
                val dayEntries=entries.filter{it.createdAt>=day&&it.createdAt<day+86400000L}
                Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Row(Modifier.fillMaxWidth().padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.width(48.dp)){
                            Text(SimpleDateFormat("dd.MM",Locale("ru")).format(Date(day)),fontWeight=FontWeight.Bold)
                            Text(listOf("вс","пн","вт","ср","чт","пт","сб")[cal.get(java.util.Calendar.DAY_OF_WEEK)-1],style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            if(dayEntries.isEmpty())Text("Нет записей",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            else dayEntries.forEach{item->
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Text("• ",color=MaterialTheme.colorScheme.primary)
                                    Text(item.title,modifier=Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick={onDelete(item)},modifier=Modifier.size(25.dp)){Icon(Icons.Default.Close,"Удалить",modifier=Modifier.size(14.dp))}
                                }
                            }
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(if(dayEntries.isEmpty())"—" else dayEntries.sumOf{it.amount}.toString()+" ₸",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseScreen(entries:List<Entry>,onAdd:()->Unit,onDelete:(Entry)->Unit,onEdit:(Entry,String,Long)->Unit){
    var editing by remember{mutableStateOf<Entry?>(null)}
    Column(Modifier.fillMaxSize()){
        Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("Добавить расход")}
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(Color(0xFFD8E3F1),RoundedCornerShape(8.dp)).padding(10.dp)){
            Text("НАЗВАНИЕ",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
            Text("СУММА",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
            Spacer(Modifier.width(68.dp))
        }
        LazyColumn{items(entries,key={it.id}){item->
            Row(Modifier.fillMaxWidth().clickable{editing=item}.padding(horizontal=8.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
                Text(item.title,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1)
                Text("${item.amount} ₸",style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
                IconButton(onClick={editing=item},modifier=Modifier.size(34.dp)){Icon(Icons.Default.Edit,"Изменить",modifier=Modifier.size(17.dp))}
                IconButton(onClick={onDelete(item)},modifier=Modifier.size(34.dp)){Icon(Icons.Default.Delete,"Удалить",modifier=Modifier.size(17.dp))}
            }
            HorizontalDivider(color=Color(0xFFDCE3EC))
        }}
    }
    editing?.let{item->
        var title by remember(item.id){mutableStateOf(item.title)}
        var amount by remember(item.id){mutableStateOf(item.amount.toString())}
        AlertDialog(onDismissRequest={editing=null},title={Text("Изменить расход")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(title,{title=it},label={Text("Расходы")},singleLine=true)
            OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()}},label={Text("Сумма, ₸")},singleLine=true)
        }},confirmButton={TextButton(enabled=title.isNotBlank()&&amount.toLongOrNull()!=null,onClick={onEdit(item,title.trim(),amount.toLong());editing=null}){Text("Сохранить")}},dismissButton={TextButton(onClick={editing=null}){Text("Отмена")}})
    }
}

@Composable
fun RequestScreen(entries:List<Entry>,onCreate:(String,String,Long,String)->Unit,onDone:(Entry)->Unit,onDelete:(Entry)->Unit){
    var title by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{Card{Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Новая заявка",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            OutlinedTextField(title,{title=it},modifier=Modifier.fillMaxWidth(),label={Text("Какая работа нужна?")})
            OutlinedTextField(address,{address=it},modifier=Modifier.fillMaxWidth(),label={Text("Адрес")})
            OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()}},modifier=Modifier.fillMaxWidth(),label={Text("Сумма, ₸")})
            OutlinedTextField(note,{note=it},modifier=Modifier.fillMaxWidth(),label={Text("Детали и комментарии")})
            Button(onClick={onCreate(title.trim(),address.trim(),amount.toLongOrNull()?:0L,note.trim());title="";address="";amount="";note=""},enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Создать заявку")}
        }}}
        item{Text("АКТИВНЫЕ ЗАЯВКИ · ${entries.size}",fontWeight=FontWeight.Bold)}
        if(entries.isEmpty())item{Text("Пока нет активных заявок")}
        items(entries,key={it.id}){item->Card{Column(Modifier.padding(14.dp)){
            Text(item.title,fontWeight=FontWeight.SemiBold);Text(item.address);Text("${item.amount} ₸")
            TextButton(onClick={onDone(item)}){Text("Выполнено → в доход")}
            TextButton(onClick={onDelete(item)}){Text("Удалить")}
        }}}
    }
}

@Composable
fun MaterialScreen(entries:List<Entry>,onSave:(String,String,String)->Unit,onDelete:(Entry)->Unit){
    val context=LocalContext.current
    var chosen by remember{mutableStateOf<android.net.Uri?>(null)}
    var title by remember{mutableStateOf("")}
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){try{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};chosen=uri}
    }
    Column(Modifier.fillMaxSize()){
        Button(onClick={picker.launch(arrayOf("image/*","video/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("Добавить фото или видео")}
        Text("Материалы карточками для визуального поиска",style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns=GridCells.Fixed(2),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            gridItems(entries,key={it.id}){item->
                Card(shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
                    Column{
                        Box(Modifier.fillMaxWidth().height(126.dp).background(Color(0xFFD8E3F1)),contentAlignment=Alignment.Center){
                            if(item.note.startsWith("image/")){
                                AndroidView(factory={ctx->ImageView(ctx).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageURI(android.net.Uri.parse(item.address))}},modifier=Modifier.fillMaxSize(),update={it.setImageURI(android.net.Uri.parse(item.address))})
                            }else Icon(Icons.Default.PlayCircle,null,tint=Color(0xFF203B63),modifier=Modifier.size(48.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(start=9.dp,end=4.dp,top=5.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(item.title,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold,maxLines=2)
                            IconButton(onClick={runCatching{context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(item.address),item.note).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))}},modifier=Modifier.size(32.dp)){Icon(Icons.Default.OpenInNew,"Открыть",modifier=Modifier.size(17.dp))}
                            IconButton(onClick={onDelete(item)},modifier=Modifier.size(32.dp)){Icon(Icons.Default.Delete,"Удалить",modifier=Modifier.size(17.dp))}
                        }
                    }
                }
            }
        }
    }
    if(chosen!=null)AlertDialog(onDismissRequest={chosen=null},title={Text("Название материала")},text={OutlinedTextField(title,{title=it},label={Text("Например, установка газлифта")})},confirmButton={TextButton(enabled=title.isNotBlank(),onClick={val u=chosen!!;onSave(title.trim(),u.toString(),context.contentResolver.getType(u)?:"application/octet-stream");chosen=null;title=""}){Text("Сохранить")}},dismissButton={TextButton(onClick={chosen=null}){Text("Отмена")}})
}
@Composable
fun EntryList(entries:List<Entry>,isRequest:Boolean,onDone:(Entry)->Unit={},onDelete:(Entry)->Unit){
    if(entries.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Пока записей нет")}
    else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){items(entries,key={it.id}){item->
        Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
            Column(Modifier.fillMaxWidth().padding(15.dp)){
                Text(item.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(item.address.isNotBlank())Text(item.address,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.amount.toString()+" ₸",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                if(item.note.isNotBlank())Text(item.note)
                Text(formatEntryDate(item.createdAt),style=MaterialTheme.typography.labelSmall)
                Row{if(isRequest)TextButton(onClick={onDone(item)}){Text("✓ Выполнено, в доход")};TextButton(onClick={onDelete(item)}){Text("Удалить")}}
            }
        }
    }}
}

@Composable
fun InfoScreen(incomes: List<Entry>, expenses: List<Entry>, requests: List<Entry>, all: List<Entry>) {
    val income = incomes.sumOf { it.amount }
    val expense = expenses.sumOf { it.amount }
    val week = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val weekIncome = incomes.filter { it.createdAt >= week }.sumOf { it.amount }
    val weekExpense = expenses.filter { it.createdAt >= week }.sumOf { it.amount }
    val month = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val monthIncome = incomes.filter { it.createdAt >= month }.sumOf { it.amount }
    val monthExpense = expenses.filter { it.createdAt >= month }.sumOf { it.amount }
    val average = if(incomes.isEmpty()) 0L else income/incomes.size
    val calDays = (0..29).map { offset -> java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR,-offset); set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0) }.timeInMillis }.reversed()
    val daily = calDays.map { day -> incomes.filter { it.createdAt >= day && it.createdAt < day + 86400000L }.sumOf { it.amount } }
    val maxDaily = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatCard("Доход за всё время", income) }
        item { StatCard("Расходы за всё время", expense) }
        item { StatCard("Чистый результат", income - expense) }
        item { StatCard("Доход за 7 дней", weekIncome) }
        item { StatCard("Расход за 7 дней", weekExpense) }
        item { StatCard("Доход за 30 дней", monthIncome) }
        item { StatCard("Расход за 30 дней", monthExpense) }
        item { StatCard("Средний чек", average) }
        item { Text("ЗАРАБОТОК ПО ДНЯМ · 30 ДНЕЙ",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.secondary) }
        items(calDays.size) { index ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(SimpleDateFormat("dd.MM",Locale("ru")).format(Date(calDays[index])),modifier=Modifier.width(48.dp),style=MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f).height(14.dp).padding(end=8.dp)) {
                    Box(Modifier.fillMaxWidth(daily[index].toFloat()/maxDaily).height(14.dp).then(Modifier).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(8.dp)))
                }
                Text("${daily[index]} ₸",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold)
            }
        }
        item { Text("Выполнено работ: ${incomes.size}", style = MaterialTheme.typography.titleMedium) }
        item { Text("Активных заявок: ${requests.size}", style = MaterialTheme.typography.titleMedium) }
        item { Text("Всего операций: ${all.count{it.type!="material"}}") }
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
                OutlinedTextField(title, { title = it }, label = { Text(if (type == "expense") "Расходы" else if(type=="income") "Проделанная работа" else "Что сделать?") }, singleLine = true)
                if (type != "expense") OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() } }, label = { Text(if (type == "request") "Договорённая сумма, ₸" else "Сумма, ₸") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0, onClick = { onSave(title.trim(), address.trim(), amount.toLong(), note.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatEntryDate(timestamp: Long): String =
    if (timestamp <= 0L) "Дата не указана"
    else SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(timestamp))

private fun priceKey(value: String): String =
    value.lowercase(Locale("ru")).replace('ё', 'е')

private fun editDistance(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 2) return 3
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

private fun tokenMatches(query: String, candidate: String): Boolean {
    if (query.length < 3 || candidate.length < 3) return false
    if (candidate.startsWith(query) || query.startsWith(candidate)) return true
    return editDistance(query, candidate) <= if (query.length >= 8) 2 else 1
}

private fun priceMatch(query: String, title: String): Boolean {
    val queryTokens = priceKey(query).filter { it.isLetterOrDigit() }.chunked(1)
    val titleKey = priceKey(title).filter { it.isLetterOrDigit() }
    val q = queryTokens.joinToString("")
    if (q.length < 3) return false
    if (titleKey.contains(q) || q.contains(titleKey)) return true
    val words = title.lowercase(Locale("ru")).replace('ё', 'е').split(Regex("[^а-яa-z0-9]+")).filter { it.length >= 3 }
    val queryWords = query.lowercase(Locale("ru")).replace('ё', 'е').split(Regex("[^а-яa-z0-9]+")).filter { it.length >= 3 }
    return queryWords.isNotEmpty() && queryWords.all { word -> words.any { tokenMatches(word, it) } }
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