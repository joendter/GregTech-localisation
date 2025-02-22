package gregtech.api.block;

import gregtech.api.GTValues;
import gregtech.api.util.GTUtility;
import gregtech.client.model.IModelSupplier;
import gregtech.client.model.SimpleStateMapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import team.chisel.ctm.client.state.CTMExtendedState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static gregtech.common.blocks.MetaBlocks.statePropertiesToString;

@Mod.EventBusSubscriber(Side.CLIENT)
public class VariantActiveBlock<T extends Enum<T> & IStringSerializable> extends VariantBlock<T> implements IModelSupplier {

    public static final ModelResourceLocation MODEL_LOCATION = new ModelResourceLocation(new ResourceLocation(GTValues.MODID, "active_blocks"), "inventory");
    public static final Object2ObjectOpenHashMap<Integer, ObjectSet<BlockPos>> ACTIVE_BLOCKS = new Object2ObjectOpenHashMap<>();
    private static final List<VariantActiveBlock<?>> INSTANCES = new ArrayList<>();
    public static final Object2ObjectOpenHashMap<Block, ObjectOpenHashSet<BlockRenderLayer>> block2blockRenderLayerMap = new Object2ObjectOpenHashMap<>();
    public static final PropertyBool ACTIVE_DEPRECATED = PropertyBool.create("active");
    public static final UnlistedBooleanProperty ACTIVE = new UnlistedBooleanProperty("active");

    public VariantActiveBlock(Material materialIn) {
        super(materialIn);
        INSTANCES.add(this);
    }

    @Override
    public IBlockState getState(T variant) {
        return super.getState(variant).withProperty(ACTIVE_DEPRECATED, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return block2blockRenderLayerMap.containsKey(state.getBlock()) && block2blockRenderLayerMap.get(state.getBlock()).contains(layer);
    }

    @Nonnull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(ACTIVE_DEPRECATED, false);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        if (state.getValue(ACTIVE_DEPRECATED)) {
            meta += 8;
        }
        return meta + state.getValue(VARIANT).ordinal();
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        Class<T> enumClass = GTUtility.getActualTypeParameter(getClass(), VariantActiveBlock.class, 0);
        this.VARIANT = PropertyEnum.create("variant", enumClass);
        this.VALUES = enumClass.getEnumConstants();
        return new ExtendedBlockState(this, new IProperty[]{VARIANT, ACTIVE_DEPRECATED}, new IUnlistedProperty[]{ACTIVE});
    }

    @Override
    public IExtendedBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        IExtendedBlockState ext = (IExtendedBlockState) state;
        if (Minecraft.getMinecraft().world == null) {
            ext = ext.withProperty(ACTIVE, false);
        } else {
            ACTIVE_BLOCKS.putIfAbsent(Minecraft.getMinecraft().world.provider.getDimension(), new ObjectOpenHashSet<>());
            ext = ext.withProperty(ACTIVE, ACTIVE_BLOCKS.get(Minecraft.getMinecraft().world.provider.getDimension()).contains(pos));
        }
        if (Loader.isModLoaded(GTValues.MODID_CTM)) {
            //if the Connected Textures Mod is loaded we wrap our IExtendedBlockState with their wrapper,
            //so that the CTM renderer can render the block properly.
            return new CTMExtendedState(ext, world, pos);
        }
        return ext;
    }

    @Override
    public void onTextureStitch(TextureStitchEvent.Pre event) {

    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onModelRegister() {
        ModelLoader.setCustomStateMapper(this, new SimpleStateMapper(MODEL_LOCATION));
        for (IBlockState state : this.getBlockState().getValidStates()) {
            //ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), this.getMetaFromState(state), new ModelResourceLocation(this.getRegistryName(), "active=true," + statePropertiesToString(state.getProperties())));
            //ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), this.getMetaFromState(state), new ModelResourceLocation(this.getRegistryName(), "active=false," + statePropertiesToString(state.getProperties())));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), this.getMetaFromState(state), new ModelResourceLocation(this.getRegistryName(), statePropertiesToString(state.getProperties())));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // low priority to capture all event-registered models
    public static void onModelBake(ModelBakeEvent event) {
        block2blockRenderLayerMap.clear();
        //Go over all VariantActiveBlock instances, then going over their model, and if they have a quad
        //to render on that render layer, add it to the map.
        for (Block b : INSTANCES) {
            for (IBlockState state : b.getBlockState().getValidStates()) {
                IBakedModel bakedModel = event.getModelRegistry().getObject(new ModelResourceLocation(b.getRegistryName(), statePropertiesToString(state.getProperties())));
                if (bakedModel != null) {
                    for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (bakedModel.getQuads(state, facing, 0).size() > 0) {
                                block2blockRenderLayerMap.putIfAbsent(b, new ObjectOpenHashSet<>());
                                block2blockRenderLayerMap.get(b).add(layer);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
