package com.example.transactional_at_jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class Order(
    id: Long? = null,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        private set

    var getParcelStatus: OrderParcelStatus = OrderParcelStatus.PADDING
        private set


    fun registerParcel() {
        getParcelStatus = OrderParcelStatus.REGISTERED
    }

    fun registerParcelFailed() {
        getParcelStatus = OrderParcelStatus.REGISTER_FAILED
    }


}
