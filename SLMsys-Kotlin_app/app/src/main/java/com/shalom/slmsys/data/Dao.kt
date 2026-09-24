package com.shalom.slmsys.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM products ORDER BY nome ASC")
    fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<Product>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Query("SELECT * FROM customers ORDER BY nome ASC")
    fun observeCustomers(): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<Customer>)

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: String)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("SELECT * FROM movements ORDER BY at DESC")
    fun observeMovements(): Flow<List<Movement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: Movement)

    @Query("DELETE FROM movements")
    suspend fun clearMovements()

    @Query("SELECT * FROM print_jobs ORDER BY at DESC LIMIT 50")
    fun observePrintJobs(): Flow<List<PrintJob>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrintJob(job: PrintJob)

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<StoreSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsOnce(): StoreSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: StoreSettings)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun productCount(): Int

    @Query("SELECT * FROM auth WHERE id = 1")
    suspend fun getAuth(): AuthState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuth(auth: AuthState)

    // ---- Fila de alterações feitas offline, aguardando internet pra subir ----

    @Query("SELECT * FROM pending_ops ORDER BY createdAt ASC")
    fun observePendingOps(): Flow<List<PendingOp>>

    @Query("SELECT * FROM pending_ops ORDER BY createdAt ASC")
    suspend fun getPendingOpsOnce(): List<PendingOp>

    @Query("SELECT COUNT(*) FROM pending_ops")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun pendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(op: PendingOp)

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun deletePendingOp(id: String)
}
