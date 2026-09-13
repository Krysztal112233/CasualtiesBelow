package dev.krysztal.casualtiesbelow.internal.sync

import dev.krysztal.casualtiesbelow.item.LiquidContents

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class InjectionSyncTest {

  @Test
  def payloadCodecRoundTrips(): Unit = {
    val payloads = List(
      InjectionBatchPayload(
        mainHand = true,
        liquid = LiquidContents.RefinedPoppyExtract.liquid,
        expectedDroplets = 405L,
        droplets = 810L,
        averageSpeed = 0.625
      ),
      InjectionBatchPayload(
        mainHand = false,
        liquid = LiquidContents.CrudePoppyLiquid.liquid,
        expectedDroplets = 0L,
        droplets = 1L,
        averageSpeed = 0.0
      )
    )
    val buffer = Unpooled.buffer()
    try {
      payloads.foreach { payload =>
        InjectionSync.Codec.encode(buffer, payload)
        assertEquals(payload, InjectionSync.Codec.decode(buffer))
      }
    } finally {
      buffer.release()
    }
  }
}
