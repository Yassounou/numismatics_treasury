# Numismatics Treasury

Numismatics Treasury is a NeoForge 1.21.1 addon for Numismatics. It expands
the banking system with direct transfers, configurable shops, player-owned
stores, and a global Auction House.

All transactions use Numismatics bank accounts directly. Players do not need
to carry physical coins or manually convert currency before buying, selling,
bidding, or transferring money.

The mod includes English text and a complete French translation.

## Features

- `/pay <player> <amount>` transfers money directly between Numismatics bank
  accounts, including accounts belonging to known offline players.
- The Transfer Teller provides the same transfer system through a dedicated
  interface.
- The Portable Transfer Terminal opens the transfer interface without a
  placed block.
- Server Shops let operators configure an item, a transaction type, an item
  quantity per lot, and a lot price. Server stock and funds are unlimited.
- Player Shops sell one configured item, have unlimited-capacity stock, and
  deposit revenue directly into Numismatics bank accounts.
- Player Shops accept automatic insertion from vanilla hoppers, Create
  funnels, and compatible item-transfer systems while rejecting items that do
  not match the configured filter.
- The global Auction House supports direct sales, timed auctions, held bids,
  configurable commissions, search, notifications, and safe item claims.
- A personal History & Statistics screen records transfers, purchases, sales,
  bids, refunds, and Player Shop revenue without changing existing shop data.
- Player Shop statistics include lifetime totals, 24-hour, 7-day and 30-day
  activity, best sale, and the revenue received by each beneficiary.
- Operators can view concise server-wide transaction volumes and Auction House
  commissions from the same interface.
- The custom interfaces use the visual direction of Numismatics and include
  full item tooltips, enchantments, custom lore, and modded components.

## Player Shop Associates

A Player Shop can have up to 16 associates. The owner manages them from a
dedicated scrollable screen and assigns an individual revenue percentage to
each associate.

- The combined associate shares cannot exceed 100%.
- The owner receives the remaining revenue and all rounding differences.
- Payments are distributed directly to every beneficiary's Numismatics bank
  account after each sale.
- The player-name field supports Tab and Shift+Tab completion for known
  Numismatics accounts, including offline players.
- Associates are notified when they are added and whenever their revenue
  percentage changes. Offline notifications are delivered on their next
  login.
- Associates can manage the shop, but only the owner or a server operator can
  edit the associate list and revenue percentages.
- Existing shops using the former single-associate format are migrated
  automatically.

## Usage

- Right-click a shop to open it.
- Sneak and right-click a Server Shop as an operator to configure its item,
  transaction type, lot size, and lot price.
- Sneak and right-click a Player Shop as its owner, an associate, or an
  operator to manage its item, stock, lot size, and lot price.
- Use the **Associates** button in Player Shop management to configure shared
  access and revenue distribution.
- Shift-click inventory stacks into the Player Shop input slot to add them to
  its stock.
- A Player Shop cannot be broken until its stock is empty.
- Right-click any Auction House block to open the server-wide marketplace.
- Right-click a Transfer Teller, or use a Portable Transfer Terminal, to enter
  a recipient and an amount. Press Tab to complete known player names.
- Open History & Statistics from the Transfer Teller, from a Player Shop's
  management screen, or with `/numismatics_treasury history`.

## Server Configuration

The server configuration is generated at:

```text
config/numismatics_treasury.json
```

It can independently enable or disable:

- `/pay`
- Transfer Teller
- Portable Transfer Terminal
- Server Shop
- Player Shop
- Auction House

It also controls payment limits, Auction House commissions, listing limits,
price limits, allowed listing durations, and History & Statistics retention.

The `history` section can enable or disable the feature, select which modules
are recorded, limit retained personal and shop entries, set the retention
period, and control access to operator-only global statistics. History is
stored separately from shops and starts recording from the moment this version
is installed; earlier transactions are not reconstructed.

Reload the configuration without restarting the server:

```text
/numismatics_treasury reload
```

This command requires operator permissions. The configuration only needs to
be edited on the server; relevant settings are synchronized to clients.

When a block module is disabled, its crafting recipe is removed after the
configuration reload and existing placed blocks become inactive. Server Shops
intentionally have no crafting recipe.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.242 or later for Minecraft 1.21.1
- Numismatics 1.0.20 or later

Numismatics is the only declared mod dependency.

## Installation

Place the Numismatics Treasury JAR in the `mods` directory on both the server
and clients, alongside Numismatics.

## Building

Numismatics is downloaded automatically from its official Maven repository:

```text
./gradlew build
```

The resulting JAR is generated in `build/libs/`.
