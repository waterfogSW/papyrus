package com.example.parallel_transaction_deadlock.repository

import com.example.parallel_transaction_deadlock.domain.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Long> {

    fun findByName(name: String): Product?
}
