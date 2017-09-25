package com.gdgnantes.devfest.android

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gdgnantes.devfest.android.features.base.app.BaseFragment
import com.gdgnantes.devfest.android.format.text.DateTimeFormatter
import com.gdgnantes.devfest.android.model.isInFuture
import com.gdgnantes.devfest.android.model.isInProgress
import com.gdgnantes.devfest.android.model.isPast
import com.gdgnantes.devfest.android.view.bind
import com.gdgnantes.devfest.android.viewmodel.Filter
import com.gdgnantes.devfest.android.viewmodel.FiltersViewModel
import com.gdgnantes.devfest.android.viewmodel.SessionsViewModel
import java.util.*


class SessionsFragment : BaseFragment() {

    companion object {

        private const val ARG_DATE = "arg:date"
        fun newInstance(date: String): SessionsFragment = SessionsFragment().apply {
            arguments = Bundle()
            arguments.putString(ARG_DATE, date)
        }

    }

    private val bookmarkManager: BookmarkManager by lazy { BookmarkManager.from(context) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var sessionsAdapter: SessionsAdapter

    private var needAutoScroll = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val model = ViewModelProviders.of(this).get(SessionsViewModel::class.java)
        model.init(arguments.getString(ARG_DATE))
        model.sessions.observe(this, Observer {
            sessionsAdapter.items = it!!
            scrollToNow()
        })

        bookmarkManager.getLiveData().observe(this, Observer {
            sessionsAdapter.updateItems()
        })

        ViewModelProviders.of(activity).get(FiltersViewModel::class.java).filters.observe(this, FiltersObserver())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sessions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionsAdapter = SessionsAdapter(context)

        emptyView = view.findViewById(R.id.empty_view)

        recyclerView = view.findViewById(android.R.id.list)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        recyclerView.adapter = sessionsAdapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        needAutoScroll = savedInstanceState == null
    }

    fun scrollToTop() {
        recyclerView.smoothScrollToPosition(0)
    }

    private fun scrollToNow() {
        if (needAutoScroll) {
            val now = Date()
            val position = sessionsAdapter.items.indexOfFirst {
                it.session.isInFuture(now) || it.session.isInProgress(now)
            }
            if (position > 0) {
                recyclerView.scrollToPosition(position)
            }
            needAutoScroll = false
        }
    }

    private fun updateAdapters() {
        val isEmpty = sessionsAdapter.items.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private inner class FiltersObserver : Observer<Set<Filter>> {
        override fun onChanged(filters: Set<Filter>?) {
            sessionsAdapter.filters = filters ?: emptySet()
        }
    }

    private inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener(onItemClickListener)
        }

        val header: TextView by view.bind<TextView>(R.id.header)
        val title: TextView by view.bind<TextView>(R.id.title)
        val subtitle: TextView by view.bind<TextView>(R.id.subtitle)
        val favoriteIndicator: View by view.bind<View>(R.id.favorite_indicator)
    }

    private inner class SessionsAdapter(
            val context: Context) : RecyclerView.Adapter<SessionViewHolder>() {

        private val tmpCalendar = Calendar.getInstance()

        private var _items: List<SessionsViewModel.Data> = emptyList()
        private var originalItems: List<SessionsViewModel.Data> = emptyList()

        var filters: Set<Filter> = emptySet()
            set(filters) {
                field = filters
                updateItems()
            }

        var items: List<SessionsViewModel.Data>
            get() = _items
            set(items) {
                originalItems = items
                updateItems()
            }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val item = items[position]

            if (position == 0 || getSectionId(position) != getSectionId(position - 1)) {
                holder.header.visibility = View.VISIBLE
                holder.header.text = DateTimeFormatter.formatHHmm(item.session.startTimestamp)
            } else {
                holder.header.visibility = View.GONE
            }

            holder.title.text = item.session.title

            if (item.room != null) {
                holder.subtitle.text = getString(R.string.session_subtitle, item.room.name)
                holder.subtitle.visibility = View.VISIBLE
            } else {
                holder.subtitle.visibility = View.GONE
            }

            if (bookmarkManager.isBookmarked(item.session.id)) {
                holder.favoriteIndicator.visibility = View.VISIBLE
            } else {
                holder.favoriteIndicator.visibility = View.GONE
            }

            val alpha = if (item.session.isPast()) 0.5f else 1f

            holder.title.alpha = alpha
            holder.subtitle.alpha = alpha
            holder.favoriteIndicator.alpha = alpha
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): SessionViewHolder {
            return SessionViewHolder(LayoutInflater.from(context).inflate(R.layout.list_item_session, parent, false))
        }

        fun updateItems() {
            if (filters.isEmpty()) {
                _items = originalItems
            } else {
                _items = originalItems.
                        filter { (session) ->
                            filters.any { it.accept(context, session) }
                        }
            }
            notifyDataSetChanged()
            updateAdapters()
        }

        private fun getSectionId(position: Int): Int {
            tmpCalendar.timeInMillis = items[position].session.startTimestamp.time
            return tmpCalendar.get(Calendar.HOUR) * 100 + tmpCalendar.get(Calendar.MINUTE)
        }
    }

    val onItemClickListener = View.OnClickListener { view ->
        val position = recyclerView.getChildLayoutPosition(view)
        if (position != RecyclerView.NO_POSITION) {
            startActivity(SessionActivity.newIntent(context, sessionsAdapter.items[position].session.id))
        }
    }

}
