package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Exercises real optional-mod inventories and tick-driven networks in an isolated world. */
public final class LogisticsChecks {
    private static int stored;
    private static final BlockPos CUBE=new BlockPos(280,300,264), SOURCE=new BlockPos(280,300,260);
    private static final BlockPos[] BASES={new BlockPos(283,300,261),new BlockPos(286,300,261)};
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void run(ServerPlayer p,String action)throws Exception{
        var l=p.serverLevel();
        var bullet=BuiltInRegistries.ITEM.get(ResourceLocation.parse("openmodularturrets:ammo_bullet"));
        check(bullet!=Items.AIR,"Open Modular Turrets ammo must be present");
        var source=(Container)l.getBlockEntity(SOURCE);
        switch(action){
            case "configure_power" -> {
                // /setblock bypasses the side configuration supplied by item placement.
                // Set the same input/output options a player selects in the cube GUI.
                var tile=l.getBlockEntity(CUBE);
                var config=tile.getClass().getMethod("getConfig").invoke(tile);
                var transmission=Class.forName("mekanism.common.lib.transmitter.TransmissionType");
                var info=config.getClass().getMethod("getConfig",transmission).invoke(config,transmission.getField("ENERGY").get(null));
                var type=Class.forName("mekanism.common.tile.component.config.DataType");
                var side=Class.forName("mekanism.api.RelativeSide");
                var set=info.getClass().getMethod("setDataType",type,side);
                set.invoke(info,type.getField("INPUT").get(null),side.getField("BACK").get(null));
                set.invoke(info,type.getField("OUTPUT").get(null),side.getField("FRONT").get(null));
                info.getClass().getMethod("setEjecting",boolean.class).invoke(info,true);
                l.invalidateCapabilities(CUBE);
            }
            case "charged" -> {
                var energy=l.getCapability(Capabilities.EnergyStorage.BLOCK,CUBE,Direction.NORTH);
                System.out.println("LUNAR_LOGISTICS_CUBE "+l.getBlockEntity(CUBE).saveWithFullMetadata(l.registryAccess()));
                System.out.println("LUNAR_LOGISTICS_GENERATOR "+l.getBlockEntity(CUBE.south()).saveWithFullMetadata(l.registryAccess()));
                check(energy!=null&&(stored=energy.getEnergyStored())>0,"Generator must charge the real energy cube");
                System.out.println("LUNAR_LOGISTICS_CUBE_CHARGED fe="+stored);
            }
            case "prepare" -> {
                source.clearContent();source.setItem(0,new ItemStack(bullet,64));source.setItem(1,new ItemStack(bullet,32));
                source.setItem(2,new ItemStack(Items.COBBLESTONE,64));
                for(int b=0;b<BASES.length;b++){
                    var inventory=l.getCapability(Capabilities.ItemHandler.BLOCK,BASES[b],Direction.NORTH);
                    check(inventory!=null,"Turret base must expose an external item handler");
                    for(int slot=0;slot<9;slot++)check(inventory.insertItem(slot,new ItemStack(bullet,slot==0?48-b*16:64),false).isEmpty(),"Ammo slot rejected bullets");
                }
                var tile=l.getBlockEntity(SOURCE.east());
                var transmitter=tile.getClass().getMethod("getTransmitter").invoke(tile);
                var mode=Class.forName("mekanism.common.lib.transmitter.ConnectionType");
                transmitter.getClass().getMethod("setConnectionTypeRaw",Direction.class,mode).invoke(transmitter,Direction.WEST,mode.getField("PULL").get(null));
                transmitter.getClass().getMethod("onModeChange",Direction.class).invoke(transmitter,Direction.WEST);
                transmitter.getClass().getMethod("refreshConnections").invoke(transmitter);
                transmitter.getClass().getMethod("notifyTileChange").invoke(transmitter);
                check(transmitter.getClass().getMethod("getConnectionTypeRaw",Direction.class).invoke(transmitter,Direction.WEST).toString().equals("PULL"),"Source pipe must be set to pull");
            }
            case "status" -> {
                String counts="";
                for(var pos:BASES){
                    var inventory=l.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.NORTH);
                    int count=0;for(int slot=0;slot<9;slot++)count+=inventory.getStackInSlot(slot).getCount();
                    counts+=(counts.isEmpty()?"":",")+count;
                }
                System.out.println("LUNAR_LOGISTICS_STOCK source="+source.countItem(bullet)+" bases="+counts);
            }
            case "filled", "refilled", "full" -> {
                int left=source.countItem(bullet), expected=action.equals("filled")?48:16;
                check(left==expected,"Source stock: expected "+expected+", got "+left);
                check(source.countItem(Items.COBBLESTONE)==64,"Pipe must leave non-ammo in the warehouse");
                for(var pos:BASES){
                    var tile=l.getBlockEntity(pos);
                    check(tile!=null,"Turret base must remain placed: "+pos+" "+l.getBlockState(pos));
                    var inventory=(Container)tile.getClass().getMethod("getInventory").invoke(tile);
                    int count=0;for(int slot=0;slot<9;slot++)count+=inventory.getItem(slot).getCount();
                    check(count==576,"Both bases must receive 9 full stacks, got "+count);
                    var energy=l.getCapability(Capabilities.EnergyStorage.BLOCK,pos,Direction.SOUTH);
                    check(energy!=null&&energy.getEnergyStored()>0,"Both bases must receive battery power after generator removal");
                    if(action.equals("filled")){
                        // External automation is insert-only; firing consumes the internal inventory.
                        check(inventory.removeItem(0,16).getCount()==16,"Simulated consumption must remove 16 rounds");
                    }
                }
                var energy=l.getCapability(Capabilities.EnergyStorage.BLOCK,CUBE,Direction.NORTH);
                check(energy.getEnergyStored()<stored,"Battery must discharge to the turret network");
                System.out.println("LUNAR_LOGISTICS_"+action.toUpperCase()+" source="+left+" cube_fe="+energy.getEnergyStored());
            }
            case "supply" -> {
                var fill=new BlockPos(289,300,260);l.setBlockAndUpdate(fill,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                var chest=(Container)l.getBlockEntity(fill);
                for(int tier=0;tier<=2;tier++){
                    chest.clearContent();SupplyDrops.fill(l,fill,tier==1?1:0,tier==2);
                    var ammo=chest.getItem(4);check(ammo.is(bullet)&&ammo.getCount()==(tier==0?16:tier==1?32:8),"All supply variants must provide the installed turret's ammunition");
                    for(int slot=0;slot<chest.getContainerSize();slot++)check(!BuiltInRegistries.ITEM.getKey(chest.getItem(slot).getItem()).getNamespace().equals("jdturrets"),"Supply must not reference the removed mod");
                }
                System.out.println("LUNAR_LOGISTICS_SUPPLY_OK normal=16 blood=32 star=8");
            }
            default -> throw new IllegalArgumentException(action);
        }
    }
}
