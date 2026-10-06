package org.geysermc.geyser.entity.type.display;

import org.cloudburstmc.math.imaginary.Quaternionf;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.FloatEntityProperty;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.EntityTypeDefinition;
import org.geysermc.geyser.entity.properties.GeyserEntityProperties;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlotDisplayEntityTest {
    @BeforeAll
    static void initializeMetadataRegistry() {
        GeyserMockContext.mockContext(context -> {
            try {
                Class.forName("org.geysermc.geyser.entity.EntityDataBehaviorRegistry");
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        });
    }

    private final GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);
    private final List<String> names = SlotDisplayEntity.properties().stream().map(p -> p.identifier().toString()).toList();

    private SlotDisplayEntity entity() {
        var properties = mock(GeyserEntityProperties.class);
        when(properties.getProperties()).thenReturn(SlotDisplayEntity.properties());
        when(properties.getPropertyIndex(anyString())).thenAnswer(call -> names.indexOf(call.getArgument(0)));
        var bedrock = mock(BedrockEntityDefinition.class);
        when(bedrock.registeredProperties()).thenReturn(properties);
        when(bedrock.identifier()).thenReturn(org.geysermc.geyser.impl.IdentifierImpl.parse("geyser:item_display"));
        var context = mock(EntitySpawnContext.class);
        when(context.session()).thenReturn(session);
        when(context.entityTypeDefinition()).thenReturn(mock(EntityTypeDefinition.class));
        when(context.bedrockEntityDefinition()).thenReturn(bedrock);
        when(context.position()).thenReturn(Vector3f.ZERO);
        when(context.motion()).thenReturn(Vector3f.ZERO);
        when(context.geyserId()).thenReturn(42L);
        return new SlotDisplayEntity(context);
    }

    @Test
    void sendsInitialEquipmentAfterSpawnEvenWhenItIsAir() {
        var entity = entity();
        entity.spawnEntity();
        var ordered = inOrder(session);
        ordered.verify(session).sendUpstreamPacket(isA(AddEntityPacket.class));
        ordered.verify(session).sendUpstreamPacket(isA(MobEquipmentPacket.class));
    }

    @Test
    void batchesBothRotationsAndScaleIntoOneRevisionWithoutMergingThem() {
        var entity = entity();
        entity.spawnEntity();
        clearInvocations(session);
        EntityMetadata<Vector3f, ?> scale = mock(EntityMetadata.class);
        when(scale.getValue()).thenReturn(Vector3f.from(-2, 0, 3));
        entity.setScale(scale);
        EntityMetadata<Quaternionf, ?> left = mock(EntityMetadata.class);
        when(left.getValue()).thenReturn(Quaternionf.from(1, 0, 0, 1));
        entity.setLeftRotation(left);
        EntityMetadata<Quaternionf, ?> right = mock(EntityMetadata.class);
        when(right.getValue()).thenReturn(Quaternionf.from(0, 1, 0, 1));
        entity.setRightRotation(right);
        entity.updateBedrockMetadata();
        var packets = ArgumentCaptor.forClass(SetEntityDataPacket.class);
        verify(session).sendUpstreamPacket(packets.capture());
        var values = packets.getValue().getProperties();
        assertEquals(1, values.getIntProperties().stream().filter(p -> p.getIndex() == names.indexOf("geyser:revision")).findFirst().orElseThrow().getValue());
        assertEquals(-2, value(values.getFloatProperties(), "sx"));
        assertEquals(0, value(values.getFloatProperties(), "sy"));
        assertEquals(Math.sqrt(.5), value(values.getFloatProperties(), "lx"), 1e-6);
        assertEquals(-Math.sqrt(.5), value(values.getFloatProperties(), "ry"), 1e-6);
        clearInvocations(session);
        entity.updateBedrockMetadata();
        verify(session, never()).sendUpstreamPacket(any());
    }

    @Test
    void rejectsNonFiniteVectorsAndPreservesValidZeroScale() {
        var entity = entity();
        entity.spawnEntity();
        clearInvocations(session);
        EntityMetadata<Vector3f, ?> scale = mock(EntityMetadata.class);
        when(scale.getValue()).thenReturn(Vector3f.from(Float.NaN, 2, 3));
        entity.setScale(scale);
        entity.updateBedrockMetadata();
        verify(session, never()).sendUpstreamPacket(any());
        assertThrows(IllegalArgumentException.class, () -> entity.setRenderProfile(-1));
        assertThrows(IllegalArgumentException.class, () -> entity.setRenderProfile(1000001));
    }

    private float value(List<FloatEntityProperty> properties, String name) {
        return properties.stream().filter(p -> p.getIndex() == names.indexOf("geyser:" + name)).findFirst().orElseThrow().getValue();
    }
}
