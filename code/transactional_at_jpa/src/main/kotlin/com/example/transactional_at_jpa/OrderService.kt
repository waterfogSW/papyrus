package com.example.transactional_at_jpa

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun applyParcelEvent(parcelEvent: ParcelEvent) {
        when (parcelEvent) {
            is ParcelEvent.Success -> {
                val order: Order = orderRepository.findById(parcelEvent.orderId).get()
                order.registerParcel()
            }

            is ParcelEvent.Failure -> {
                val order: Order = orderRepository.findById(parcelEvent.orderId).get()
                order.registerParcelFailed()
            }
        }
    }

}
