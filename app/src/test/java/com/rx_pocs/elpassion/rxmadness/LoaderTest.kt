package com.rx_pocs.elpassion.rxmadness

import com.nhaarman.mockito_kotlin.*
import org.junit.Test
import rx.Observable
import rx.functions.Action1
import rx.subjects.PublishSubject

class LoaderTest {

    @Test
    fun shouldShowLoaderOnCreate() {
        val loaderVisibilities = mock<Action1<Boolean>>()
        onCreate(Observable.just({ q -> Observable.just(Person("1")) }), Observable.just(Unit), Observable.never(), loaderVisibilities, mock(), mock(), Observable.never()).subscribe()

        verify(loaderVisibilities).call(true)
    }

    @Test
    fun shouldHideLoaderAfterApiCallEnd() {
        val loaderVisibilities = mock<Action1<Boolean>>()
        onCreate(Observable.just({ q -> Observable.just(Person("1")) }), Observable.just(Unit), Observable.never(), loaderVisibilities, mock(), mock(), Observable.never()).subscribe()

        verify(loaderVisibilities).call(false)
    }

    @Test
    fun shouldNotHideLoaderBeforeApiCallEnd() {
        val loaderVisibilities = mock<Action1<Boolean>>()
        onCreate(Observable.never(), Observable.just(Unit), Observable.never(), loaderVisibilities, mock(), mock(), Observable.never()).subscribe()

        verify(loaderVisibilities, never()).call(false)
    }

    @Test
    fun shouldNotShowLoaderBeforeOnCreate() {
        val loaderVisibilities = mock<Action1<Boolean>>()
        onCreate(Observable.just({ q -> Observable.just(Person("1")) }), Observable.never(), Observable.never(), loaderVisibilities, mock(), mock(), Observable.never()).subscribe()

        verify(loaderVisibilities, never()).call(true)
    }

    @Test
    fun shouldShowErrorWhenCallToApiFail() {
        val showError = mock<Action1<in Throwable>>()
        onCreate(Observable.error(RuntimeException()), Observable.just(Unit), Observable.never(), mock(), showError, mock(), Observable.never()).subscribe()

        verify(showError).call(any())
    }

    @Test
    fun shouldHideLoaderWhenErrorOccurs() {
        val loaderVisibilities = mock<Action1<Boolean>>()
        onCreate(Observable.error(RuntimeException()), Observable.just(Unit), Observable.never(), loaderVisibilities, mock(), mock(), Observable.never()).subscribe()

        verify(loaderVisibilities).call(false)
    }

    @Test
    fun shouldShowResultsFromApi() {
        val showResults = mock<Action1<in Person>>()
        onCreate(Observable.just({ q -> Observable.just(Person("1")) }), Observable.just(Unit), Observable.never(), mock(), mock(), showResults, Observable.never()).subscribe()

        verify(showResults).call(Person("1"))
    }

    @Test
    fun shouldShowResultsFromApiAfterQueryChanged() {
        val showResults = mock<Action1<in Person>>()
        val queryChanges = PublishSubject.create<String>()
        onCreate(Observable.just({ q -> Observable.just(Person(q)) }), Observable.just(Unit), Observable.never(), mock(), mock(), showResults, queryChanges).subscribe()
        reset(showResults)

        queryChanges.onNext("query")
        verify(showResults).call(Person("query"))
    }
}

data class Person(val id: String)

fun onCreate(api: Observable<(String) -> Observable<Person>>,
             onCreateSubject: Observable<Unit>,
             onDestroyObservable: Observable<Unit>,
             loaderVisibilities: Action1<Boolean>,
             showError: Action1<in Throwable>,
             showResults: Action1<in Person>,
             queryChanges: Observable<String>) =
        onCreateImpl(onCreateSubject, onDestroyObservable, queryChanges, showResults,
                api.composeLoader(loaderVisibilities)
                        .composeError(showError))

private fun onCreateImpl(onCreateObservable: Observable<Unit>,
                         onDestroyObservable: Observable<Unit>,
                         queryChanges: Observable<String>,
                         showResults: Action1<in Person>,
                         apiWithLoaderAndError: Observable<(String) -> Observable<Person>>): Observable<Person> {
    return apiWithLoaderAndError
            .subscribeToApiOnCreate(onCreateObservable)
            .mergeWith(apiWithLoaderAndError.subscribeToApiOnQueryChanges(queryChanges))
            .takeUntil(onDestroyObservable)
            .doOnNext(showResults)
}

private fun Observable<(String) -> Observable<Person>>.subscribeToApiOnCreate(onCreateSubject: Observable<Unit>) = onCreateSubject.switchMap { flatMap { it("") } }

private fun <T> Observable<T>.composeError(showError: Action1<in Throwable>) =
        doOnError(showError).onErrorResumeNext(Observable.empty())

private fun <T> Observable<T>.composeLoader(loaderVisibilities: Action1<Boolean>) =
        doOnSubscribe { loaderVisibilities.call(true) }
                .doOnTerminate { loaderVisibilities.call(false) }

fun Observable<(String) -> Observable<Person>>.subscribeToApiOnQueryChanges(queryChanges: Observable<String>) = queryChanges.switchMap { q -> flatMap { it(q) } }
