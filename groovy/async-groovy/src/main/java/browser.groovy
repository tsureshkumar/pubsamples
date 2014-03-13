import groovy.swing.SwingBuilder
import rx.Observable
import rx.Subscription
import rx.observables.SwingObservable
import rx.schedulers.Schedulers
import rx.schedulers.SwingScheduler
import rx.subscriptions.BooleanSubscription

import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.directory.Attribute
import javax.naming.directory.SearchControls
import javax.naming.directory.SearchResult
import javax.naming.ldap.InitialLdapContext
import javax.naming.ldap.LdapContext
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import java.awt.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class Global {
    public static Executor executor() { return Executors.newFixedThreadPool(5) }
}

class LdapAsync {
    public static Observable<LdapContext> connectLdap(String server, String uname, String pwd) {
        def env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, server);
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, uname);
        env.put(Context.SECURITY_CREDENTIALS, pwd);
        env.put("java.naming.ldap.attributes.binary", "networkAddress masvDefaultRange nDSPKIPublicKeyCertificate nDSPKIPrivateKey nDSPKIPublicKey nDSPKICertificateChain nDSPKITrustedRootCertificate loginIntruderAddress masvAuthorizedRange");
        Future<LdapContext> f = Global.executor().submit({
            new InitialLdapContext(env, null);
        } as Callable);
        def v = Observable.from(f, Schedulers.io())
        v
    }

    public
    static Observable<SearchResult> search(InitialLdapContext ldap, String base, String attr, SearchControls sc) {
        Observable.create { observer ->
            Subscription sub1 = new BooleanSubscription()
            def answers = ldap.search(base, attr, sc)
            while (answers.hasMore() && !sub1.isUnsubscribed()) {
                def res = answers.next()
                observer.onNext(res)
            }
            answers.close()
            observer.onCompleted()
            sub1
        }
    }

}


class LdapBrowser {

    class SearchResultRenderer extends JLabel implements ListCellRenderer<SearchResult> {
        @Override
        Component getListCellRendererComponent(JList<? extends SearchResult> jList, SearchResult sr, int i, boolean isSelected, boolean b2) {
            setText(sr.attributes.get("cn").get().toString())
            if (isSelected) {
                setBackground(jList.getSelectionBackground());
                setForeground(jList.getSelectionForeground());
            } else {
                setBackground(jList.getBackground());
                setForeground(jList.getForeground());
            }
            setEnabled(jList.isEnabled());
            setFont(jList.getFont());
            setOpaque(true);
            return this;
        }
    }

    def swing = new SwingBuilder()
    def listD = new DefaultListModel<SearchResult>()
    def obs
    def values
    def server_field
    def search_context
    def uname
    def pwd
    def btn_load
    def txtFilter
    def tableDataModel = []

    Observable<JButton> clicksO;         // stream of load clicks
    Observable<String> filterValuesO;   // stream of filter values
    Observable<LdapContext> ldapO; // ldap connection
    Observable<Observable<List<SearchResult>>> resultOO; // stream of search results
    Subscription loadingResult = null;

    public LdapBrowser() {
        build_gui()
        init_observables();
    }

    private build_gui() {

        swing.frame(title: 'Frame', size: [840, 400], defaultCloseOperation: JFrame.EXIT_ON_CLOSE, show: true, pack: true) {
            vbox {
                hbox { label("Server IP"); server_field = textField("ldap://164.99.86.149:389") }
                hbox { label("User"); uname = textField("cn=admin,o=novell") }
                hbox { label("Password"); pwd = textField("**********") }
                hbox { label("context"); search_context = textField("o=novell") }
                this.btn_load = button(text: 'Load')
                hbox { label("CN Seach (contains)"); txtFilter = textField("(cn=)", enabled: false) }
                panel {
                    splitPane() {
                        scrollPane(constraints: "left", preferredSize: [160, -1]) {
                            obs = list(model: listD)
                        }
                        scrollPane(constraints: "top") {
                            values = table {
                                tableModel(list: tableDataModel) {
                                    propertyColumn(header: 'Name', propertyName: 'name')
                                    propertyColumn(header: 'Value', propertyName: 'value')
                                }
                            }
                        }
                    }
                }
            }
        }


        obs.setCellRenderer(new SearchResultRenderer())
    }

    private init_observables() {
        clicksO = SwingObservable
                .fromButtonAction(btn_load)

        // Observable combining button click and connecting to ldap
        ldapO = SwingObservable
                .fromButtonAction(btn_load)
                .flatMap({ LdapAsync.connectLdap(server_field.text, uname.text, pwd.text).cache() })

        // Observable for changing text values in filter text box
        filterValuesO = SwingObservable
                .fromKeyEvents(txtFilter)
                .map({
            (it.getComponent() as JTextField).getText()
        })
                .sample(200, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()

        // observable of observable of List of Cn when button clicked and ldap successfully connected
        // and text box values sampled
        resultOO = Observable.combineLatest(ldapO, filterValuesO, { x, y -> [x, y] })
                .observeOn(Schedulers.io())
                .map { ldap, filter ->
            def sc = new SearchControls()
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
            LdapAsync.search(ldap, search_context.text, filter, sc).subscribeOn(Schedulers.io())
        }
        .map { it.toList() }

    }

    def printThread(x) { println Thread.currentThread().getName() + " " + x }


    Observable<String> fromList(JList list) {
        Observable.create { observer ->
            list.addListSelectionListener(new ListSelectionListener() {
                @Override
                void valueChanged(ListSelectionEvent listSelectionEvent) {
                    if (!listSelectionEvent.getValueIsAdjusting())
                        observer.onNext(listSelectionEvent)
                }
            })
        }
    }


    private displayDetailedResult() {
        fromList(obs)
                .observeOn(SwingScheduler.instance)
                .subscribe { ListSelectionEvent ev ->
            SearchResult sr = obs.getSelectedValue()
            tableDataModel.clear()
            if (sr != null) {
                NamingEnumeration<Attribute> attrs = sr.getAttributes().getAll()
                while (attrs.hasMore()) {
                    def attr = (Attribute) attrs.next();
                    String name = attr.getID()
                    Object v = attr.get()
                    println(attr.getClass().getName())
                    if (!(v instanceof byte[])) {
                        String value = attr.get().toString()
                        println(name)
                        println(value)
                        tableDataModel << [name: name, value: value]
                    }
                }
            }
            values.dataModel.fireTableDataChanged()
        } {
            println(it.getMessage())
            displayDetailedResult() // resubscribe
        }
    }

    private Subscription displaySearchResult() {
        // subscribe to observable of Observable list and update the list data
        return Observable.combineLatest(ldapO, filterValuesO, { x, y -> [x, y] })
                .observeOn(Schedulers.io())
                .subscribe { ldap, filter ->
            def sc = new SearchControls()
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
            printThread("searching ....")
            if (loadingResult != null)
                loadingResult.unsubscribe()
            loadingResult = LdapAsync.search(ldap, search_context.text, filter, sc).subscribeOn(Schedulers.io())
                    .toList()
                    .observeOn(SwingScheduler.instance)
                    .subscribe { list ->
                obs.listData = list
                tableDataModel.clear()
                if (list.size() > 0) {
                    obs.setSelectedIndex(0)
                }
            } {
                println(it.getMessage())
            }
        }

    }

    public def main() {

        ldapO
                .observeOn(SwingScheduler.instance)
                .subscribe {
            txtFilter.setEnabled(true)
        } {
            printThread("error: " + it.getMessage());
            txtFilter.setEnabled(false)
            main()
        }

        displaySearchResult();
        displayDetailedResult()
    }

}

new LdapBrowser().main()
