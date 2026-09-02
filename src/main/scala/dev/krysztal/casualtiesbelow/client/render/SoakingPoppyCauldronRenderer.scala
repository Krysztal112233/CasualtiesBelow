package dev.krysztal.casualtiesbelow.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

import dev.krysztal.casualtiesbelow.block.entity.SoakingPoppyCauldronBlockEntity
import dev.krysztal.casualtiesbelow.item.CasualtiesBelowItems

/** Renders the consumed paste as a real item model floating over the soaking liquid. */
final class SoakingPoppyCauldronRenderer(context: BlockEntityRendererProvider.Context)
    extends BlockEntityRenderer[
      SoakingPoppyCauldronBlockEntity,
      SoakingPoppyCauldronRenderState
    ] {

  private val itemModelResolver: ItemModelResolver = context.itemModelResolver()
  // Renderer providers are created before registry holders bind their default components.
  private lazy val renderedPaste = new ItemStack(CasualtiesBelowItems.CrudePoppyPaste)

  override def createRenderState(): SoakingPoppyCauldronRenderState =
    SoakingPoppyCauldronRenderState()

  override def extractRenderState(
      blockEntity: SoakingPoppyCauldronBlockEntity,
      state: SoakingPoppyCauldronRenderState,
      partialTicks: Float,
      cameraPosition: Vec3,
      breakProgress: ModelFeatureRenderer.CrumblingOverlay | Null
  ): Unit = {
    super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)
    val level = blockEntity.getLevel
    state.animationTime =
      if (level == null) 0.0f
      else (level.getGameTime % SoakingPoppyCauldronRenderer.AnimationPeriod).toFloat + partialTicks
    itemModelResolver.updateForTopItem(
      state.renderedItem,
      renderedPaste,
      ItemDisplayContext.FIXED,
      level,
      null,
      java.lang.Long.hashCode(blockEntity.getBlockPos.asLong())
    )
  }

  override def submit(
      state: SoakingPoppyCauldronRenderState,
      poseStack: PoseStack,
      submitNodeCollector: SubmitNodeCollector,
      camera: CameraRenderState
  ): Unit = {
    val bob = Mth.sin(state.animationTime * SoakingPoppyCauldronRenderer.BobAngularSpeed) * 0.01f
    val bounds = state.renderedItem.getModelBoundingBox

    poseStack.pushPose()
    poseStack.translate(0.5, SoakingPoppyCauldronRenderer.ItemHeight + bob, 0.5)
    poseStack.mulPose(Axis.YP.rotationDegrees(state.animationTime * 0.5f))
    poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
    poseStack.scale(0.35f, 0.35f, 0.35f)
    poseStack.translate(
      -(bounds.minX + bounds.maxX) / 2.0,
      -(bounds.minY + bounds.maxY) / 2.0,
      -(bounds.minZ + bounds.maxZ) / 2.0
    )
    state.renderedItem.submit(
      poseStack,
      submitNodeCollector,
      state.lightCoords,
      OverlayTexture.NO_OVERLAY,
      0
    )
    poseStack.popPose()
  }
}

final class SoakingPoppyCauldronRenderState extends BlockEntityRenderState {
  val renderedItem = new ItemStackRenderState()
  var animationTime: Float = 0.0f
}

private object SoakingPoppyCauldronRenderer {
  val ItemHeight: Double = 0.96
  val AnimationPeriod: Long = 720L
  val BobAngularSpeed: Float = Mth.TWO_PI / 80.0f
}
