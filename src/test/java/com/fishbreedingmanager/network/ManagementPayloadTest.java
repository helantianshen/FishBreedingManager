package com.fishbreedingmanager.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 编解码边界在 JSON 解析之前限制网络分配大小 */
class ManagementPayloadTest {
    /** 验证分块序号与数据往返，并在申请过大请求缓冲区前拒绝声明长度 */
    @Test void roundTripsChunkAndRejectsOversizedRequest() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var sent = new ManagementReplyPayload(17, "snapshot", 2, 3, new byte[]{1, 2, 3});
            ManagementReplyPayload.CODEC.encode(buf, sent);
            var decoded = ManagementReplyPayload.CODEC.decode(buf);
            assertEquals(sent.request(), decoded.request()); assertEquals(2, decoded.index());
            assertArrayEquals(sent.data(), decoded.data());
            assertThrows(IllegalArgumentException.class, () -> ManagementRequestPayload.CODEC.encode(buf,
                    new ManagementRequestPayload(new byte[24577])));
            buf.clear(); buf.writeVarInt(24577);
            assertThrows(RuntimeException.class, () -> ManagementRequestPayload.CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
