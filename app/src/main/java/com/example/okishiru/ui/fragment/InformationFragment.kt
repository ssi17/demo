package com.example.okishiru.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.okishiru.R
import com.example.okishiru.databinding.FragmentInformationBinding
import com.example.okishiru.json.Article
import com.example.okishiru.model.MainViewModel
import com.example.okishiru.ui.recycler.ArticleRecyclerAdapter

class InformationFragment: Fragment() {

    private var binding: FragmentInformationBinding? = null
    private val sharedViewModel: MainViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentBinding = FragmentInformationBinding.inflate(inflater, container, false)
        binding = fragmentBinding
        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.informationFragment = this
        binding?.lifecycleOwner = viewLifecycleOwner

        // 再生状態なら現在地をもとにコンテンツを取得
        // 再生状態でなければTopTitleを表示
        if (sharedViewModel.displayArticles.value!!.size == 0 || !sharedViewModel.startFlag) {
            binding!!.topTitle.setImageResource(R.drawable.top_title)
            binding!!.startText.setText(R.string.start_text)
        } else {
            setInformationTitle()
        }

        // 記事リストが更新されるタイミングで画面を更新
        sharedViewModel.displayArticles.observe(viewLifecycleOwner) {
            it.setRecyclerView()
            if (it.size != 0) {
                binding!!.topTitle.setImageDrawable(null)
                binding!!.startText.text = null
                setInformationTitle()
            }
        }
    }

    private fun setInformationTitle() {
        binding!!.informationTitle.setImageResource(R.drawable.header_title_information)
    }

    // リサイクラーを設定
    private fun List<Article>.setRecyclerView() {
        recyclerView = binding!!.recyclerView
        val adapter = ArticleRecyclerAdapter(this, sharedViewModel.flagList)
        recyclerView.adapter = adapter
        // お気に入り登録ボタンが押された時の処理
        adapter.favoriteButton = object : ArticleRecyclerAdapter.FavoriteButton {
            override fun pushFavoriteButton(id: Int) {
                sharedViewModel.changeFavoriteFlag(id)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(requireActivity())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
