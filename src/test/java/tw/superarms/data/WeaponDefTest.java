package tw.superarms.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeaponDefTest {

    @Test
    void unlimitedWeaponIsForSale() {
        WeaponDef weapon = new WeaponDef(UUID.randomUUID());

        weapon.sellUntil(0);

        assertTrue(weapon.isForSale(System.currentTimeMillis()));
    }

    @Test
    void weaponStopsBeingForSaleAtItsCutoff() {
        WeaponDef weapon = new WeaponDef(UUID.randomUUID());
        long cutoff = 1_000L;
        weapon.sellUntil(cutoff);

        assertTrue(weapon.isForSale(cutoff - 1));
        assertFalse(weapon.isForSale(cutoff));
        assertFalse(weapon.isForSale(cutoff + 1));
    }
}
