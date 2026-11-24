package com.huimaotech.invoicevault
import android.app.Application
import androidx.room.Room
class VaultApp:Application(){ val db by lazy { Room.databaseBuilder(this,VaultDatabase::class.java,"invoice-vault.db").addMigrations(VaultDatabase.MIGRATION_1_2,VaultDatabase.MIGRATION_2_3).build() } }
