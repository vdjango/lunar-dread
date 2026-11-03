package lunar.dread;

import com.tik.zbb.Constants;
import com.tik.zbb.ai.action.actions.breakk.BreakAction;
import com.tik.zbb.ai.action.actions.build.BuildAction;

/** ZBB publishes real post-edit events on its own bus, including the old block state. */
public final class ZbbRecoveryBridge {
    static void register(){Constants.EVENT_BUS.register(new ZbbRecoveryBridge());}

    @org.greenrobot.eventbus.Subscribe
    public void broken(BreakAction.OnAnyBlockBrokenEvent event){
        BlockRecovery.record(event.level(),event.pos(),event.oldState(),event.level().getBlockState(event.pos()));
    }
    @org.greenrobot.eventbus.Subscribe
    public void built(BuildAction.OnAnyBlockPlacedEvent event){
        BlockRecovery.record(event.level(),event.pos(),event.oldState(),event.level().getBlockState(event.pos()));
    }
}
