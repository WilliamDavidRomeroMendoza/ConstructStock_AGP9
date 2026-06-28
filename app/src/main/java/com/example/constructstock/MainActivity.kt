package com.example.constructstock

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Product(
    val id: Int,
    val name: String,
    val category: String,
    val stock: Int,
    val location: String,
    val minStock: Int
)

data class Movement(
    val id: Int,
    val productName: String,
    val type: String,
    val quantity: Int,
    val responsible: String,
    val date: String
)

class InventoryDb(context: Context) : SQLiteOpenHelper(context, "construct_stock.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                stock INTEGER NOT NULL,
                location TEXT NOT NULL,
                min_stock INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                product_name TEXT NOT NULL,
                type TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                responsible TEXT NOT NULL,
                date TEXT NOT NULL
            )
            """.trimIndent()
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS movements")
        db.execSQL("DROP TABLE IF EXISTS products")
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        insertProduct(db, "Taladro Bosch", "Herramienta", 8, "Almacén A", 3)
        insertProduct(db, "Amoladora Makita", "Herramienta", 5, "Almacén A", 2)
        insertProduct(db, "Cemento Tipo I", "Material", 120, "Almacén B", 20)
        insertProduct(db, "Varilla de acero", "Material", 40, "Almacén C", 10)
    }

    private fun insertProduct(db: SQLiteDatabase, name: String, category: String, stock: Int, location: String, minStock: Int) {
        val values = ContentValues().apply {
            put("name", name)
            put("category", category)
            put("stock", stock)
            put("location", location)
            put("min_stock", minStock)
        }
        db.insert("products", null, values)
    }

    fun products(search: String = "", category: String? = null): List<Product> {
        val list = mutableListOf<Product>()
        val args = mutableListOf<String>()
        val whereParts = mutableListOf<String>()
        if (search.isNotBlank()) {
            whereParts.add("name LIKE ?")
            args.add("%$search%")
        }
        if (!category.isNullOrBlank()) {
            whereParts.add("category = ?")
            args.add(category)
        }
        val where = if (whereParts.isEmpty()) null else whereParts.joinToString(" AND ")
        val cursor = readableDatabase.query("products", null, where, args.toTypedArray(), null, null, "name ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Product(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        stock = it.getInt(it.getColumnIndexOrThrow("stock")),
                        location = it.getString(it.getColumnIndexOrThrow("location")),
                        minStock = it.getInt(it.getColumnIndexOrThrow("min_stock"))
                    )
                )
            }
        }
        return list
    }

    fun addProduct(name: String, category: String, stock: Int, location: String, minStock: Int) {
        val values = ContentValues().apply {
            put("name", name)
            put("category", category)
            put("stock", stock)
            put("location", location)
            put("min_stock", minStock)
        }
        writableDatabase.insert("products", null, values)
    }

    fun updateProduct(product: Product) {
        val values = ContentValues().apply {
            put("name", product.name)
            put("category", product.category)
            put("stock", product.stock)
            put("location", product.location)
            put("min_stock", product.minStock)
        }
        writableDatabase.update("products", values, "id=?", arrayOf(product.id.toString()))
    }

    fun deleteProduct(productId: Int) {
        writableDatabase.delete("products", "id=?", arrayOf(productId.toString()))
    }

    fun addMovement(product: Product, type: String, quantity: Int, responsible: String): Boolean {
        if (quantity <= 0) return false
        val newStock = if (type == "Salida") product.stock - quantity else product.stock + quantity
        if (newStock < 0) return false

        writableDatabase.beginTransaction()
        try {
            val productValues = ContentValues().apply { put("stock", newStock) }
            writableDatabase.update("products", productValues, "id=?", arrayOf(product.id.toString()))

            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val movementValues = ContentValues().apply {
                put("product_id", product.id)
                put("product_name", product.name)
                put("type", type)
                put("quantity", quantity)
                put("responsible", responsible.ifBlank { "Administrador" })
                put("date", date)
            }
            writableDatabase.insert("movements", null, movementValues)
            writableDatabase.setTransactionSuccessful()
            return true
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun movements(): List<Movement> {
        val list = mutableListOf<Movement>()
        val cursor = readableDatabase.query("movements", null, null, null, null, null, "id DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Movement(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        productName = it.getString(it.getColumnIndexOrThrow("product_name")),
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        quantity = it.getInt(it.getColumnIndexOrThrow("quantity")),
                        responsible = it.getString(it.getColumnIndexOrThrow("responsible")),
                        date = it.getString(it.getColumnIndexOrThrow("date"))
                    )
                )
            }
        }
        return list
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var db: InventoryDb
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = InventoryDb(this)
        showHome()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun base(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(245, 248, 250))
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(Color.rgb(21, 96, 130))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(titleView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return root
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 16f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun label(text: String, size: Float = 15f, color: Int = Color.DKGRAY): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun showHome() {
        val root = base("ConstructStock")
        root.addView(label("Sistema de inventario para empresas de construcción", 16f, Color.DKGRAY))
        root.addView(label("Control de herramientas, materiales, entradas, salidas y stock disponible.", 14f, Color.GRAY))
        root.addView(space())
        root.addView(button("🔧 Herramientas") { showInventory("Herramienta") })
        root.addView(button("🧱 Materiales") { showInventory("Material") })
        root.addView(button("📦 Inventario general") { showInventory(null) })
        root.addView(button("➕ Agregar producto") { showProductDialog(null) })
        root.addView(button("⬆ Registrar entrada") { showMovementScreen("Entrada") })
        root.addView(button("⬇ Registrar salida") { showMovementScreen("Salida") })
        root.addView(button("📋 Historial de movimientos") { showHistory() })
        root.addView(button("⚠ Reporte de stock bajo") { showLowStock() })
        setContentView(root)
    }

    private fun space(): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(10))
    }

    private fun showInventory(category: String?) {
        val root = base(if (category == null) "Inventario" else category)
        val search = EditText(this).apply {
            hint = "Buscar producto"
            setSingleLine(true)
            setText(currentSearch)
        }
        root.addView(search)
        root.addView(button("Buscar") {
            currentSearch = search.text.toString()
            showInventory(category)
        })
        root.addView(button("Agregar producto") { showProductDialog(null) })
        root.addView(button("Volver") { showHome() })

        val scroll = ScrollView(this)
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val products = db.products(currentSearch, category)
        if (products.isEmpty()) {
            listContainer.addView(label("No hay productos registrados."))
        } else {
            products.forEach { product -> listContainer.addView(productCard(product, category)) }
        }
        scroll.addView(listContainer)
        
        val scrollParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
        root.addView(scroll, scrollParams)
        
        setContentView(root)
    }

    private fun productCard(product: Product, category: String?): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        val alert = if (product.stock <= product.minStock) "  ⚠ Stock bajo" else ""
        card.addView(label("${product.name}$alert", 18f, if (product.stock <= product.minStock) Color.rgb(190, 50, 40) else Color.rgb(21, 96, 130)))
        card.addView(label("Categoría: ${product.category}"))
        card.addView(label("Stock disponible: ${product.stock}"))
        card.addView(label("Ubicación: ${product.location}"))
        card.addView(label("Stock mínimo: ${product.minStock}"))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Editar") { showProductDialog(product) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("Eliminar") { confirmDelete(product, category) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)

        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
        card.layoutParams = params
        return card
    }

    private fun showProductDialog(product: Product?) {
        val isEdit = product != null
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val name = EditText(this).apply { hint = "Nombre"; setText(product?.name ?: "") }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Herramienta", "Material"))
            setSelection(if (product?.category == "Material") 1 else 0)
        }
        val stock = EditText(this).apply { hint = "Cantidad"; inputType = 2; setText(product?.stock?.toString() ?: "") }
        val location = EditText(this).apply { hint = "Ubicación"; setText(product?.location ?: "") }
        val minStock = EditText(this).apply { hint = "Stock mínimo"; inputType = 2; setText(product?.minStock?.toString() ?: "") }
        form.addView(label("Nombre")); form.addView(name)
        form.addView(label("Categoría")); form.addView(category)
        form.addView(label("Cantidad")); form.addView(stock)
        form.addView(label("Ubicación")); form.addView(location)
        form.addView(label("Stock mínimo")); form.addView(minStock)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Editar producto" else "Agregar producto")
            .setView(form)
            .setPositiveButton(if (isEdit) "Guardar" else "Agregar") { _, _ ->
                val pName = name.text.toString().trim()
                if (pName.isBlank()) {
                    toast("Ingrese el nombre del producto")
                    return@setPositiveButton
                }
                val qty = stock.text.toString().toIntOrNull() ?: 0
                val min = minStock.text.toString().toIntOrNull() ?: 1
                val loc = location.text.toString().ifBlank { "Almacén" }
                if (product == null) {
                    db.addProduct(pName, category.selectedItem.toString(), qty, loc, min)
                } else {
                    db.updateProduct(product.copy(name = pName, category = category.selectedItem.toString(), stock = qty, location = loc, minStock = min))
                }
                currentSearch = ""
                showInventory(null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDelete(product: Product, category: String?) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Deseas eliminar ${product.name}?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.deleteProduct(product.id)
                showInventory(category)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showMovementScreen(type: String) {
        val root = base("Registrar $type")
        val products = db.products()
        if (products.isEmpty()) {
            root.addView(label("Primero registre productos."))
            root.addView(button("Volver") { showHome() })
            setContentView(root)
            return
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { "${it.name} - Stock: ${it.stock}" })
        }
        val quantity = EditText(this).apply { hint = "Cantidad"; inputType = 2 }
        val responsible = EditText(this).apply { hint = "Responsable" }

        root.addView(label("Producto")); root.addView(spinner)
        root.addView(label("Cantidad")); root.addView(quantity)
        root.addView(label("Responsable")); root.addView(responsible)
        root.addView(button("Registrar $type") {
            val selected = products[spinner.selectedItemPosition]
            val qty = quantity.text.toString().toIntOrNull() ?: 0
            val ok = db.addMovement(selected, type, qty, responsible.text.toString())
            if (ok) {
                toast("$type registrada correctamente")
                showHome()
            } else {
                toast("Cantidad inválida o stock insuficiente")
            }
        })
        root.addView(button("Volver") { showHome() })
        setContentView(root)
    }

    private fun showHistory() {
        val root = base("Historial")
        root.addView(button("Volver") { showHome() })
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val movements = db.movements()
        if (movements.isEmpty()) {
            list.addView(label("Aún no hay movimientos registrados."))
        } else {
            movements.forEach { m ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setBackgroundColor(Color.WHITE)
                }
                card.addView(label("${m.type}: ${m.productName}", 18f, Color.rgb(21, 96, 130)))
                card.addView(label("Cantidad: ${m.quantity}"))
                card.addView(label("Responsable: ${m.responsible}"))
                card.addView(label("Fecha: ${m.date}"))
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(8), 0, dp(8))
                }
                card.layoutParams = params
                list.addView(card)
            }
        }
        scroll.addView(list)
        
        val scrollParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
        root.addView(scroll, scrollParams)
        
        setContentView(root)
    }

    private fun showLowStock() {
        val root = base("Stock bajo")
        root.addView(button("Volver") { showHome() })
        val low = db.products().filter { it.stock <= it.minStock }
        if (low.isEmpty()) {
            root.addView(label("No hay productos con stock bajo.", 16f, Color.rgb(20, 130, 70)))
        } else {
            low.forEach { product ->
                root.addView(label("⚠ ${product.name}: stock ${product.stock}, mínimo ${product.minStock}", 16f, Color.rgb(190, 50, 40)))
            }
        }
        setContentView(root)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
