package com.example.transactional_at_jpa

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class TestCode(
    @Autowired private val orderService: OrderService,
    @Autowired private val orderRepository: OrderRepository,
) {

    @Test
    @DisplayName("택배 등록 실패 이벤트가 발생하면, 주문의 택배 등록 상태가 실패로 변경된다.")
    @Transactional
    fun checkOrderStatus() {
        // given
        val order = Order()
        val savedOrder: Order = orderRepository.save(order)
        val failedParcelEvent = ParcelEvent.Failure(savedOrder.id!!)

        // when
        orderService.applyParcelEvent(failedParcelEvent)

        // then
        val findOrder: Order = orderRepository.findById(savedOrder.id!!).get()
        Assertions.assertThat(findOrder.getParcelStatus).isEqualTo(OrderParcelStatus.REGISTER_FAILED)
    }


}
