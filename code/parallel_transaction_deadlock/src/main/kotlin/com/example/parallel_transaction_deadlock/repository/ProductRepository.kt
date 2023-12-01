package com.example.parallel_transaction_deadlock.repository

import com.example.parallel_transaction_deadlock.domain.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {

    fun existsByName(name: String): Boolean
}
