/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.account;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

/// Manages Microsoft accounts without alternate authentication providers.
@NotNullByDefault
public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    /// Rendered Microsoft account rows.
    private final ObservableList<AccountListItem> items;

    /// Navigation title.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));

    /// Accounts bound to the application's Microsoft account store.
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());

    /// Selected Microsoft game identity.
    private final ObjectProperty<@Nullable Account> selectedAccount;

    /// Creates the Microsoft account page.
    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);
    }

    /// Returns the selected account binding.
    public ObjectProperty<@Nullable Account> selectedAccountProperty() { return selectedAccount; }

    /// Returns the displayed account collection.
    public ListProperty<Account> accountsProperty() { return accounts; }

    /// Returns the navigation state.
    @Override public ReadOnlyObjectProperty<State> stateProperty() { return state.getReadOnlyProperty(); }

    /// Creates the standard HMCL account layout with Microsoft as its sole provider.
    @Override protected Skin<?> createDefaultSkin() { return new AccountListPageSkin(this); }

    /// Renders the standard account navigation and saved account rows.
    @NotNullByDefault
    private static final class AccountListPageSkin extends DecoratorAnimatedPageSkin<AccountListPage> {
        /// Creates provider navigation and the account list.
        private AccountListPageSkin(AccountListPage page) {
            super(page);
            var microsoft = new AdvancedListItem();
            microsoft.getStyleClass().add("navigation-drawer-item");
            microsoft.setTitle(i18n("account.methods.microsoft"));
            microsoft.setLeftIcon(SVG.MICROSOFT);
            microsoft.setOnAction(event -> {
                Runnable open = () -> Controllers.dialog(new MicrosoftAccountLoginPane());
                if (SettingsManager.isUserGameAccountsReadOnly()) {
                    Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                        SettingsManager.forceOverwriteUserGameAccounts();
                        open.run();
                    });
                } else { open.run(); }
            });
            var methods = new VBox(new ClassTitle(i18n("account.create").toUpperCase(Locale.ROOT)), microsoft);
            methods.getStyleClass().add("advanced-list-box-content");
            FXUtils.setLimitWidth(methods, 200);
            setLeft(new ScrollPane(methods));
            var rows = new VBox();
            rows.setSpacing(10);
            rows.getStyleClass().add("card-list");
            Bindings.bindContent(rows.getChildren(), page.items);
            var list = new ScrollPane(rows);
            list.setFitToWidth(true);
            rows.maxWidthProperty().bind(list.widthProperty());
            FXUtils.smoothScrolling(list);
            setCenter(list);
        }
    }
}
