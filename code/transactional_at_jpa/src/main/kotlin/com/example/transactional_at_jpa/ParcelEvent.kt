package com.example.transactional_at_jpa

sealed class ParcelEvent() {

    data class Success(
        val orderId: Long,
    ) : ParcelEvent()

    data class Failure(
        val orderId: Long,
    ) : ParcelEvent()
}
